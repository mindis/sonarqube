/*
 * Copyright (C) 2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.performance;

import com.sonar.orchestrator.Orchestrator;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ServerTest extends PerfTestCase {

  @Test
  public void server_startup_and_shutdown() throws Exception {
    Orchestrator orchestrator = Orchestrator.builderEnv().build();
    try {
      // TODO re-nable assertion
      //long startupDuration = start(orchestrator);
      //assertDurationAround(startupDuration, 45000);
      orchestrator.start();

      long shutdownDuration = stop(orchestrator);
      // can't use percent margins because logs are second-grained but not milliseconds
      assertDurationLessThan(shutdownDuration, 2500);

    } finally {
      orchestrator.stop();
    }
  }

  long start(Orchestrator orchestrator) throws IOException {
    ServerLogs.clear(orchestrator);
    orchestrator.start();
    return logsPeriod(orchestrator);

  }

  long stop(Orchestrator orchestrator) throws Exception {
    ServerLogs.clear(orchestrator);
    orchestrator.stop();
    return logsPeriod(orchestrator);
  }

  private long logsPeriod(Orchestrator orchestrator) throws IOException {
    // compare dates of first and last log
    List<String> lines = FileUtils.readLines(orchestrator.getServer().getLogs());
    if (lines.size() < 2) {
      throw new IllegalStateException("Fail to estimate server shutdown or startup duration. Not enough logs.");
    }
    Date start = ServerLogs.extractFirstDate(lines);
    Collections.reverse(lines);
    Date end = ServerLogs.extractFirstDate(lines);
    return end.getTime() - start.getTime();
  }
}
