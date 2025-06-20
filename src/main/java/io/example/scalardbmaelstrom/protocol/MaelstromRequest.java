package io.example.scalardbmaelstrom.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MaelstromRequest {
  private String src;
  private String dest;
  private Map<String, Object> body;

  public String getSrc() {
    return src;
  }

  public void setSrc(String src) {
    this.src = src;
  }

  public String getDest() {
    return dest;
  }

  public void setDest(String dest) {
    this.dest = dest;
  }

  public Map<String, Object> getBody() {
    return body;
  }

  public void setBody(Map<String, Object> body) {
    this.body = body;
  }

  public String getType() {
    return body != null ? (String) body.get("type") : null;
  }

  public Integer getMsgId() {
    return body != null ? (Integer) body.get("msg_id") : null;
  }

  public String getKey() {
    if (body != null) {
      Object key = body.get("key");
      return key != null ? String.valueOf(key) : null;
    }
    return null;
  }

  public Object getValue() {
    return body != null ? body.get("value") : null;
  }

  public Object getFrom() {
    return body != null ? body.get("from") : null;
  }

  public Object getTo() {
    return body != null ? body.get("to") : null;
  }

  public String getNodeId() {
    return body != null ? (String) body.get("node_id") : null;
  }

  public String[] getNodeIds() {
    if (body != null && body.get("node_ids") != null) {
      Object nodeIds = body.get("node_ids");
      if (nodeIds instanceof String[]) {
        return (String[]) nodeIds;
      } else if (nodeIds instanceof java.util.List) {
        @SuppressWarnings("unchecked")
        java.util.List<String> list = (java.util.List<String>) nodeIds;
        return list.toArray(new String[0]);
      }
    }
    return null;
  }
}