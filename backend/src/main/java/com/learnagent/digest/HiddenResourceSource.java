package com.learnagent.digest;

import jakarta.persistence.*;

@Entity
@Table(name = "hidden_resource_sources")
public class HiddenResourceSource {
  @Id @Column(length = 253) private String host;
  protected HiddenResourceSource() { }
  public HiddenResourceSource(String host) { this.host = host; }
  public String getHost() { return host; }
}
