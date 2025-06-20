package io.example.scalardbmaelstrom.kv;

import com.scalar.db.api.ConditionBuilder;
import com.scalar.db.api.DistributedStorage;
import com.scalar.db.api.Get;
import com.scalar.db.api.MutationCondition;
import com.scalar.db.api.Put;
import com.scalar.db.api.Result;
import com.scalar.db.exception.storage.ExecutionException;
import com.scalar.db.exception.storage.NoMutationException;
import com.scalar.db.io.Key;
import com.scalar.db.service.StorageFactory;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KvStorageService implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(KvStorageService.class);
  private static final String NAMESPACE = "maelstrom";
  private static final String TABLE = "maelstrom_kv";
  private static final String KEY_COLUMN = "key";
  private static final String VALUE_COLUMN = "value";

  private final DistributedStorage storage;

  public KvStorageService(String configPath) throws IOException {
    StorageFactory factory = StorageFactory.create(configPath);
    this.storage = factory.getStorage();
  }

  public Optional<String> read(String key) throws ExecutionException {
    Get get = new Get(new Key(KEY_COLUMN, key)).forNamespace(NAMESPACE).forTable(TABLE);

    Optional<Result> result = storage.get(get);
    if (result.isPresent() && result.get().getColumns().containsKey(VALUE_COLUMN)) {
      String value = result.get().getValue(VALUE_COLUMN).get().getAsString().get();
      return Optional.of(value);
    }
    return Optional.empty();
  }

  public void write(String key, String value) throws ExecutionException {
    Put put =
        new Put(new Key(KEY_COLUMN, key))
            .forNamespace(NAMESPACE)
            .forTable(TABLE)
            .withValue(VALUE_COLUMN, value);

    storage.put(put);
  }

  public boolean cas(String key, String expectedValue, String newValue)
      throws ExecutionException {
    try {
      // Use the old API format for ScalarDB v3.15.0
      Put put =
          new Put(new Key(KEY_COLUMN, key))
              .forNamespace(NAMESPACE)
              .forTable(TABLE)
              .withValue(VALUE_COLUMN, newValue);

      if (expectedValue == null) {
        // For null expected value, use putIfNotExists condition
        MutationCondition condition = ConditionBuilder.putIfNotExists();
        put = put.withCondition(condition);
      } else {
        // For non-null expected value, use conditional put
        MutationCondition condition =
            ConditionBuilder.putIf(
                    ConditionBuilder.column(VALUE_COLUMN).isEqualToText(expectedValue))
                .build();
        put = put.withCondition(condition);
      }

      storage.put(put);
      return true;
    } catch (NoMutationException e) {
      // Condition failed - expected value didn't match current value
      return false;
    }
  }

  @Override
  public void close() throws Exception {
    if (storage != null) {
      storage.close();
    }
  }
}