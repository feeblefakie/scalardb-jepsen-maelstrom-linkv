# ScalarDB Jepsen Maelstrom Lin-KV

A ScalarDB-based implementation of a Lin-KV server compatible with Jepsen Maelstrom's lin-kv workload.

## Prerequisites

- Java 21 or higher
- Gradle
- PostgreSQL (tested with PostgreSQL 14)
- Maelstrom (for distributed testing)

## Setup

### 1. Install and Start PostgreSQL

On macOS with Homebrew:

```bash
# Install PostgreSQL
brew install postgresql@14

# Link PostgreSQL binaries
brew link --overwrite postgresql@14

# Initialize the database cluster
initdb -D /opt/homebrew/var/postgresql@14

# Start PostgreSQL service
brew services start postgresql@14

# Create database and user for ScalarDB
psql -U $USER -d postgres <<EOF
CREATE USER scalardb WITH PASSWORD 'scalardb';
CREATE DATABASE scalardb OWNER scalardb;
GRANT ALL PRIVILEGES ON DATABASE scalardb TO scalardb;
EOF
```

To stop PostgreSQL when not in use:
```bash
brew services stop postgresql@14
```

### 2. Configure Database Connection

The `database.properties` file is already configured for PostgreSQL:

```properties
scalar.db.storage=jdbc
scalar.db.jdbc.connection_pool.min_idle=5
scalar.db.jdbc.connection_pool.max_idle=10
scalar.db.jdbc.connection_pool.max_total=25
scalar.db.jdbc.url=jdbc:postgresql://localhost:5432/scalardb
scalar.db.jdbc.username=scalardb
scalar.db.jdbc.password=scalardb
```

### 3. Create Database Schema

Use ScalarDB Schema Loader to create the required table:

```bash
java -jar scalardb-schema-loader-3.15.0.jar --config database.properties --schema-file scalardb-schema/schema.json
```

### 4. Build the Project

```bash
./gradlew shadowJar
```

## Usage

### Run Standalone

```bash
java -jar build/libs/scalardb-maelstrom-kv.jar
```

The server reads JSON messages from stdin and writes responses to stdout.

#### Example Usage

```bash
# Interactive mode - type messages one by one
java -jar build/libs/scalardb-maelstrom-kv.jar

# Pipe input from echo
echo '{"type": "init", "msg_id": 1, "src": "c1", "dest": "n1"}' | java -jar build/libs/scalardb-maelstrom-kv.jar

# Pipe multiple commands
echo -e '{"type": "init", "msg_id": 1, "src": "c1", "dest": "n1"}\n{"type": "write", "msg_id": 2, "src": "c1", "dest": "n1", "key": "foo", "value": "bar"}\n{"type": "read", "msg_id": 3, "src": "c1", "dest": "n1", "key": "foo"}' | java -jar build/libs/scalardb-maelstrom-kv.jar

# From file
cat input.jsonl | java -jar build/libs/scalardb-maelstrom-kv.jar
```

### Test with Sample Input

```bash
# Test with predefined requests
cat test-requests.json | java -jar build/libs/scalardb-maelstrom-kv.jar
```

### Run with Maelstrom

First install Maelstrom from [Jepsen's Maelstrom repository](https://github.com/jepsen-io/maelstrom):

```bash
# Download and install Maelstrom
wget https://github.com/jepsen-io/maelstrom/releases/latest/download/maelstrom.tar.bz2
tar -xf maelstrom.tar.bz2
```

#### Single Key Testing

```bash
# Clear database and run single-key test
psql -d scalardb -c "DELETE FROM maelstrom.maelstrom_kv;" && \
./maelstrom/maelstrom test -w lin-kv --bin ./maelstrom-wrapper.sh \
  --concurrency 10 --time-limit 5 --rate 10 --key-count 1
```

#### Multiple Key Testing

```bash
# Clear database and run multi-key test (3 keys)
psql -d scalardb -c "DELETE FROM maelstrom.maelstrom_kv;" && \
./maelstrom/maelstrom test -w lin-kv --bin ./maelstrom-wrapper.sh \
  --concurrency 30 --time-limit 10 --rate 10 --key-count 3
```

**Important Notes:**
- Always clear the database before tests to avoid state contamination
- For multiple keys, use `concurrency = 10 × key-count` (lin-kv requirement)
- The command must be on a single line (no line breaks after `--bin`)

### Test Results

**✅ All tests pass linearizability checks!**

The implementation successfully demonstrates:
- **Linearizable operations** across single and multiple keys
- **Atomic CAS operations** using ScalarDB's conditional Put
- **Correct Maelstrom protocol implementation**
- **No data races** in single-threaded design
- **Persistent storage** with PostgreSQL backend

**Sample output:**
```
Everything looks good! ヽ('ー`)ノ
```

## Protocol

The server implements the Maelstrom lin-kv protocol with the following operations:

- `init`: Initialize the node
- `read`: Read a value by key
- `write`: Write a value to a key
- `cas`: Compare-and-set operation

### Request Examples

```json
{"type": "init", "msg_id": 1, "src": "c1", "dest": "n1"}
{"type": "read", "key": "foo", "msg_id": 2, "src": "c1", "dest": "n1"}
{"type": "write", "key": "foo", "value": "bar", "msg_id": 3, "src": "c1", "dest": "n1"}
{"type": "cas", "key": "foo", "from": "bar", "to": "baz", "msg_id": 4, "src": "c1", "dest": "n1"}
```

### Response Examples

```json
{"src": "n1", "dest": "c1", "body": {"type": "init_ok", "in_reply_to": 1}}
{"src": "n1", "dest": "c1", "body": {"type": "read_ok", "value": "bar", "in_reply_to": 2}}
{"src": "n1", "dest": "c1", "body": {"type": "write_ok", "in_reply_to": 3}}
{"src": "n1", "dest": "c1", "body": {"type": "cas_ok", "in_reply_to": 4}}
{"src": "n1", "dest": "c1", "body": {"type": "error", "code": 22, "in_reply_to": 4, "text": "expected value does not match"}}
```

## Architecture

- **Main.java**: Entry point that handles stdin/stdout communication
- **MaelstromRequest/Response**: Protocol message POJOs with nested JSON structure support
- **KvStorageService**: ScalarDB DistributedStorage wrapper implementing:
  - `read()`: Direct key-value retrieval
  - `write()`: Key-value storage
  - `cas()`: Atomic compare-and-swap using ScalarDB's conditional Put
- **JsonUtil**: Jackson JSON serialization utilities

The implementation is single-threaded by design, as Maelstrom handles concurrency by running multiple node processes.

### Key Implementation Details

1. **Conditional Put for CAS**: Uses `ConditionBuilder.putIf()` for atomic operations without transactions
2. **Error Handling**: Proper Maelstrom error codes (20 for key not found, 22 for CAS failure)
3. **Database Schema**: Simple key-value table in PostgreSQL via ScalarDB
4. **No Transactions**: Uses only DistributedStorage API for linearizability

## References

- [ScalarDB OSS](https://github.com/scalar-labs/scalardb)
- [Maelstrom](https://github.com/jepsen-io/maelstrom)
- [Maelstrom lin-kv workload](https://github.com/jepsen-io/maelstrom/blob/main/doc/workloads.md#workload-lin-kv)
