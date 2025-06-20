package io.example.scalardbmaelstrom.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonUtil {
  private static final ObjectMapper mapper = new ObjectMapper();

  static {
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
  }

  public static <T> T fromJson(String json, Class<T> clazz) throws Exception {
    return mapper.readValue(json, clazz);
  }

  public static String toJson(Object obj) throws Exception {
    return mapper.writeValueAsString(obj);
  }
}