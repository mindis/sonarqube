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

package org.sonar.server.source.ws;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.persistence.DbSession;
import org.sonar.server.component.ComponentTesting;
import org.sonar.server.component.db.ComponentDao;
import org.sonar.server.db.DbClient;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.source.SourceService;
import org.sonar.server.ws.WsTester;

import java.util.Collections;

import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RawActionTest {

  @Mock
  DbClient dbClient;

  @Mock
  DbSession session;

  @Mock
  ComponentDao componentDao;

  @Mock
  SourceService sourceService;

  WsTester tester;

  ComponentDto project = ComponentTesting.newProjectDto();
  ComponentDto file = ComponentTesting.newFileDto(project);

  @Before
  public void setUp() throws Exception {
    when(dbClient.componentDao()).thenReturn(componentDao);
    when(dbClient.openSession(false)).thenReturn(session);
    tester = new WsTester(new SourcesWs(mock(ShowAction.class), new RawAction(dbClient, sourceService), mock(ScmAction.class)));
  }

  @Test
  public void get_txt() throws Exception {
    String fileKey = "src/Foo.java";
    when(componentDao.getByKey(session, fileKey)).thenReturn(file);

    when(sourceService.getLinesAsTxt(session, fileKey)).thenReturn(newArrayList(
      "public class HelloWorld {",
      "}"
      ));

    WsTester.TestRequest request = tester.newGetRequest("api/sources", "raw").setParam("key", fileKey);
    String result = request.execute().outputAsString();
    assertThat(result).isEqualTo("public class HelloWorld {\r\n}\r\n");
  }

  @Test(expected = NotFoundException.class)
  public void fail_to_get_txt_when_file_does_not_exists() throws Exception {
    WsTester.TestRequest request = tester.newGetRequest("api/sources", "raw").setParam("key", "unknown");
    request.execute();
  }

  public void fail_to_get_txt_when_no_source() throws Exception {
    String fileKey = "src/Foo.java";
    when(componentDao.getByKey(session, fileKey)).thenReturn(file);
    when(sourceService.getLinesAsTxt(session, fileKey)).thenReturn(Collections.<String>emptyList());

    WsTester.TestRequest request = tester.newGetRequest("api/sources", "raw").setParam("key", fileKey);
    try {
      request.execute();
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(NotFoundException.class).hasMessage("");
    }
  }
}