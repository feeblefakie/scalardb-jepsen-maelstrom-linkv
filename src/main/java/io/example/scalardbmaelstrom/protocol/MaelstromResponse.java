package io.example.scalardbmaelstrom.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MaelstromResponse {
  private String src;
  private String dest;
  private Map<String, Object> body = new HashMap<>();

  public MaelstromResponse() {}

  public MaelstromResponse(String src, String dest) {
    this.src = src;
    this.dest = dest;
  }

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

  public void setType(String type) {
    body.put("type", type);
  }

  public void setInReplyTo(Integer msgId) {
    body.put("in_reply_to", msgId);
  }

  public void setValue(Object value) {
    body.put("value", value);
  }

  public void setCode(int code) {
    body.put("code", code);
  }

  public void setText(String text) {
    body.put("text", text);
  }

  public static MaelstromResponse initOk(String src, String dest, Integer msgId) {
    MaelstromResponse response = new MaelstromResponse(src, dest);
    response.setType("init_ok");
    response.setInReplyTo(msgId);
    return response;
  }

  public static MaelstromResponse readOk(String src, String dest, Integer msgId, Object value) {
    MaelstromResponse response = new MaelstromResponse(src, dest);
    response.setType("read_ok");
    response.setInReplyTo(msgId);
    response.setValue(value);
    return response;
  }

  public static MaelstromResponse writeOk(String src, String dest, Integer msgId) {
    MaelstromResponse response = new MaelstromResponse(src, dest);
    response.setType("write_ok");
    response.setInReplyTo(msgId);
    return response;
  }

  public static MaelstromResponse casOk(String src, String dest, Integer msgId) {
    MaelstromResponse response = new MaelstromResponse(src, dest);
    response.setType("cas_ok");
    response.setInReplyTo(msgId);
    return response;
  }

  public static MaelstromResponse fail(
      String src, String dest, Integer msgId, int code, String text) {
    MaelstromResponse response = new MaelstromResponse(src, dest);
    response.setType("error");
    response.setInReplyTo(msgId);
    response.setCode(code);
    response.setText(text);
    return response;
  }
}