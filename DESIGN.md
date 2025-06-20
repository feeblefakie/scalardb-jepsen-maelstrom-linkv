# ScalarDB × Maelstrom lin-kv サーバ設計書

## 概要

- **目的**: ScalarDB OSS（v3.15.0）の DistributedStorage を利用して Maelstrom lin-kv ワークロード互換のKVサーバを Java 21 で実装
- **特徴**
  - Java 21
  - Gradle（Kotlin DSL）with Shadow plugin for fat JAR
  - 1ノード1スレッド（シングルスレッド）設計
  - Maelstromプロトコル(JSON)による入出力
  - CRUDのみ・トランザクションなし（条件付きPutでCAS実装）
  - PostgreSQLバックエンド（JDBCアダプタ経由）
  - ScalarDB 設定は `database.properties`
  - テーブルは **ScalarDB Schema Loader** で作成

---

## ディレクトリ構成

```plaintext
scalardb-maelstrom-kv/
├ build.gradle.kts
├ database.properties
├ scalardb-schema/
│   └ schema.yaml
├ src/
│   └ main/
│       └ java/
│           └ io/
│               └ example/
│                   └ scalardbmaelstrom/
│                       ├ Main.java
│                       ├ protocol/
│                       │   ├ MaelstromRequest.java
│                       │   └ MaelstromResponse.java
│                       └ kv/
│                           └ KvStorageService.java
│                       └ util/
│                           └ JsonUtil.java
````

---

## テーブル定義（ScalarDB Schema Loader 用 JSON）

`scalardb-schema/schema.json`

```json
{
  "maelstrom.maelstrom_kv": {
    "transaction": false,
    "partition-key": ["key"],
    "columns": {
      "key": "TEXT",
      "value": "TEXT"
    }
  }
}
```

* テーブル作成は以下のように実施します（v3.15.0ではJSON形式が必須）

  ```
  java -jar scalardb-schema-loader-3.15.0.jar --config database.properties --schema-file scalardb-schema/schema.json
  ```

  * 詳細は [公式ドキュメント](https://scalardb.scalar-labs.com/docs/latest/schema-loader/) 参照

---

## build.gradle.kts

```kotlin
plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.scalar-labs:scalardb:3.15.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("org.postgresql:postgresql:42.7.3")
}

application {
    mainClass.set("io.example.scalardbmaelstrom.Main")
}

shadowJar {
    archiveBaseName.set("scalardb-maelstrom-kv")
    archiveClassifier.set("")
    archiveVersion.set("")
}
```

---

## プロトコル仕様（lin-kv）

### リクエスト例

```json
{"src": "c1", "dest": "n1", "body": {"type": "init", "msg_id": 1}}
{"src": "c1", "dest": "n1", "body": {"type": "read", "key": "foo", "msg_id": 2}}
{"src": "c1", "dest": "n1", "body": {"type": "write", "key": "foo", "value": "bar", "msg_id": 3}}
{"src": "c1", "dest": "n1", "body": {"type": "cas", "key": "foo", "from": "bar", "to": "baz", "msg_id": 4}}
```

### レスポンス例

```json
{"src": "n1", "dest": "c1", "body": {"type": "init_ok", "in_reply_to": 1}}
{"src": "n1", "dest": "c1", "body": {"type": "read_ok", "value": "bar", "in_reply_to": 2}}
{"src": "n1", "dest": "c1", "body": {"type": "write_ok", "in_reply_to": 3}}
{"src": "n1", "dest": "c1", "body": {"type": "cas_ok", "in_reply_to": 4}}
{"src": "n1", "dest": "c1", "body": {"type": "fail", "code": 11, "in_reply_to": 4, "text": "compare-and-set failed"}}
```

---

## 主なクラス設計

### Main.java

* エントリーポイント
* 標準入力から1行ずつJSON読み込み（Jacksonでデシリアライズ）
* `body.type`で分岐し各操作ハンドラを呼び出す
* 処理結果をJSONで標準出力に返却
* 全体をシングルスレッドで直列処理

### protocol/MaelstromRequest.java, MaelstromResponse.java

* MaelstromプロトコルのJSONメッセージを表すPOJO（Jacksonで変換）

### kv/KvStorageService.java

* ScalarDBの`DistributedStorage`を用いたKV CRUDラッパ

  * `read(key)` … 値取得（Optional<String>で返却）
  * `write(key, value)` … 値のアップサート
  * `cas(key, expectedValue, newValue)` … 条件付きPutを使用したatomic Compare-And-Set実装
    - `expectedValue`がnullの場合: `putIfNotExists()`を使用
    - それ以外: `ConditionBuilder.putIf(column.isEqualToText(expectedValue))`を使用
    - 成功時true、期待値不一致時false（NoMutationException）を返却

### util/JsonUtil.java

* JacksonのObjectMapperラッパー（ヘルパー）

---

## エラーハンドリング

* 例外時やCAS失敗時などは、`fail`タイプでエラーを返す
* Maelstromリクエスト`msg_id`に対応する`in_reply_to`でレスポンス

---

## ビルド・実行

* **ビルド**

  ```sh
  ./gradlew shadowJar
  ```
* **スタンドアロン起動**

  ```sh
  java -jar build/libs/scalardb-maelstrom-kv.jar
  ```
* **Maelstromテスト実行例**

  ```sh
  # データベースクリア後、単一キーテスト
  psql -d scalardb -c "DELETE FROM maelstrom.maelstrom_kv;" && \
  ./maelstrom/maelstrom test -w lin-kv --bin ./maelstrom-wrapper.sh \
    --concurrency 10 --time-limit 5 --rate 10 --key-count 1
  
  # データベースクリア後、複数キーテスト（3キー）
  psql -d scalardb -c "DELETE FROM maelstrom.maelstrom_kv;" && \
  ./maelstrom/maelstrom test -w lin-kv --bin ./maelstrom-wrapper.sh \
    --concurrency 30 --time-limit 10 --rate 10 --key-count 3
  ```

---

## 注意事項

* シングルスレッドで十分（Maelstromが各ノードを別プロセスで起動し、全体で並列性を実現）
* Maelstromプロトコル仕様を厳密に守ること（ネストされたJSON構造に注意）
* ScalarDB OSSは **Schema Loader でのみテーブル作成**できる（SQL不可）
* **テスト実行前にデータベースをクリアすること**（状態汚染を防ぐため）
* lin-kvワークロードは複数キー使用時、`concurrency = 10 × key-count`が必要
* コマンドラインでは`--bin`の後に改行を入れないこと

---

## 参考リンク

* [ScalarDB OSS](https://github.com/scalar-labs/scalardb)
* [Schema Loaderドキュメント](https://scalardb.scalar-labs.com/docs/latest/schema-loader/)
* [ScalarDB API Guide (Conditional Put)](https://scalardb.scalar-labs.com/docs/latest/api-guide/#conditions-for-put)
* [Maelstrom (Jepsen)](https://github.com/jepsen-io/maelstrom)
* [Maelstrom workload: lin-kv](https://github.com/jepsen-io/maelstrom/blob/main/doc/workloads.md#workload-lin-kv)

---

## まとめ

* Java 21、ScalarDB 3.15.0、DistributedStorage利用
* Maelstrom lin-kvプロトコル互換サーバ（シングルスレッド、CRUDのみ）
* PostgreSQLバックエンド、条件付きPutでatomic CAS実装
* Schema Loader（JSON形式）でテーブル作成
* Gradle（Kotlin DSL）+ Shadow pluginでfat JARビルド
* **全テスト合格**: linearizabilityチェックをパス


