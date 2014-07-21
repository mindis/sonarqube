/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.batch.mediumtest.issues;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.analyzer.issue.AnalyzerIssue;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.batch.mediumtest.AnalyzerMediumTester;
import org.sonar.batch.mediumtest.AnalyzerMediumTester.TaskResult;
import org.sonar.batch.mediumtest.xoo.plugin.XooPlugin;
import org.sonar.batch.protocol.input.ActiveRule;

import java.io.File;
import java.io.IOException;

import static org.fest.assertions.Assertions.assertThat;

public class IssuesMediumTest {

  @org.junit.Rule
  public TemporaryFolder temp = new TemporaryFolder();

  public AnalyzerMediumTester tester = AnalyzerMediumTester.builder()
    .registerPlugin("xoo", new XooPlugin())
    .addDefaultQProfile("xoo", "Sonar Way")
    .activateRule(new ActiveRule("xoo", "OneIssuePerLine", "MAJOR", "xoo", "xoo"))
    .bootstrapProperties(ImmutableMap.of("sonar.analysis.mode", "sensor"))
    .build();

  @Before
  public void prepare() {
    tester.start();
  }

  @After
  public void stop() {
    tester.stop();
  }

  @Test
  public void scanSampleProject() throws Exception {
    File projectDir = new File(IssuesMediumTest.class.getResource("/mediumtest/xoo/sample").toURI());

    TaskResult result = tester
      .newScanTask(new File(projectDir, "sonar-project.properties"))
      .start();

    assertThat(result.issues()).hasSize(24);
  }

  @Test
  public void testIssueExclusion() throws Exception {
    File projectDir = new File(IssuesMediumTest.class.getResource("/mediumtest/xoo/sample").toURI());

    TaskResult result = tester
      .newScanTask(new File(projectDir, "sonar-project.properties"))
      .property("sonar.issue.ignore.allfile", "1")
      .property("sonar.issue.ignore.allfile.1.fileRegexp", "object")
      .start();

    assertThat(result.issues()).hasSize(19);
  }

  @Test
  public void scanTempProject() throws IOException {

    File baseDir = temp.newFolder();
    File srcDir = new File(baseDir, "src");
    srcDir.mkdir();

    File xooFile = new File(srcDir, "sample.xoo");
    File xooMeasureFile = new File(srcDir, "sample.xoo.measures");
    FileUtils.write(xooFile, "Sample xoo\ncontent");
    FileUtils.write(xooMeasureFile, "lines:20");

    TaskResult result = tester.newTask()
      .properties(ImmutableMap.<String, String>builder()
        .put("sonar.task", "scan")
        .put("sonar.projectBaseDir", baseDir.getAbsolutePath())
        .put("sonar.projectKey", "com.foo.project")
        .put("sonar.projectName", "Foo Project")
        .put("sonar.projectVersion", "1.0-SNAPSHOT")
        .put("sonar.projectDescription", "Description of Foo Project")
        .put("sonar.sources", "src")
        .build())
      .start();

    assertThat(result.issues()).hasSize(20);

    boolean foundIssueAtLine1 = false;
    for (AnalyzerIssue issue : result.issues()) {
      if (issue.line() == 1) {
        foundIssueAtLine1 = true;
        assertThat(issue.inputFile()).isEqualTo(new DefaultInputFile("src/sample.xoo"));
        assertThat(issue.message()).isEqualTo("This issue is generated on each line");
        assertThat(issue.effortToFix()).isNull();
      }
    }
    assertThat(foundIssueAtLine1).isTrue();
  }

}