/*
 * Copyright (C) 2009-2012 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */

package com.sonar.it.permission.suite;

import com.sonar.it.ItUtils;
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.SonarRunner;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.selenium.Selenese;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonar.wsclient.SonarClient;
import org.sonar.wsclient.base.HttpException;
import org.sonar.wsclient.issue.BulkChange;
import org.sonar.wsclient.issue.BulkChangeQuery;
import org.sonar.wsclient.issue.Issue;
import org.sonar.wsclient.issue.IssueQuery;
import org.sonar.wsclient.permissions.PermissionParameters;
import org.sonar.wsclient.user.UserParameters;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

public class IssuePermissionTest {

  @ClassRule
  public static Orchestrator orchestrator = PermissionTestSuite.ORCHESTRATOR;

  @Before
  public void init() {
    orchestrator.getDatabase().truncateInspectionTables();

    orchestrator.getServer().restoreProfile(FileLocation.ofClasspath("/com/sonar/it/permission/one-issue-per-line-profile.xml"));
    SonarRunner sampleProject = SonarRunner.create(ItUtils.locateProjectDir("shared/xoo-sample"))
      .withoutDynamicAnalysis()
      .setProfile("one-issue-per-line");
    orchestrator.executeBuild(sampleProject);

    SonarRunner sampleProject2 = SonarRunner.create(ItUtils.locateProjectDir("shared/xoo-sample"))
      .withoutDynamicAnalysis()
      .setProperty("sonar.projectKey", "sample2")
      .setProfile("one-issue-per-line");
    orchestrator.executeBuild(sampleProject2);
  }

  /**
   * SONAR-4686
   */
  @Test
  public void need_code_viewer_permission_to_see_source_code_from_issue_detail() {
    SonarClient client = orchestrator.getServer().adminWsClient();

    String withCodeViewerPermission = "with-code-viewer-permission";
    String withoutCodeViewerPermission = "without-code-viewer-permission";

    try {
      client.userClient().create(UserParameters.create().login(withCodeViewerPermission).name(withCodeViewerPermission)
        .password("password").passwordConfirmation("password"));
      client.permissionClient().addPermission(PermissionParameters.create().user(withCodeViewerPermission).component("sample").permission("codeviewer"));

      client.userClient().create(UserParameters.create().login(withoutCodeViewerPermission).name(withoutCodeViewerPermission)
        .password("password").passwordConfirmation("password"));
      // By default, it's the group anyone that have the permission codeviewer, it would be better to remove all groups on this permission
      client.permissionClient().removePermission(PermissionParameters.create().group("anyone").component("sample").permission("codeviewer"));

      orchestrator.executeSelenese(Selenese.builder().setHtmlTestsInClasspath("need-code-viewer-permission-to-see-source-code-from-issue-detail",
        "/selenium/permission/issue-permissions/without-code-viewer-permission-source-code-from-issue-detail-is-hidden.html",
        "/selenium/permission/issue-permissions/with-code-viewer-permission-source-code-from-issue-detail-is-visible.html"
        ).build());

    } finally {
      client.userClient().deactivate(withCodeViewerPermission);
      client.userClient().deactivate(withoutCodeViewerPermission);
    }
  }

  @Test
  public void need_user_permission_on_project_to_see_issue() {
    SonarClient client = orchestrator.getServer().adminWsClient();

    String withBrowsePermission = "with-browse-permission";
    ;
    String withoutBrowsePermission = "without-browse-permission";

    try {
      client.userClient().create(UserParameters.create().login(withBrowsePermission).name(withBrowsePermission)
        .password("password").passwordConfirmation("password"));
      client.permissionClient().addPermission(PermissionParameters.create().user(withBrowsePermission).component("sample").permission("user"));

      client.userClient().create(UserParameters.create().login(withoutBrowsePermission).name(withoutBrowsePermission)
        .password("password").passwordConfirmation("password"));
      // By default, it's the group anyone that have the permission user, it would be better to remove all groups on this permission
      client.permissionClient().removePermission(PermissionParameters.create().group("anyone").component("sample").permission("user"));

      // Without user permission, a user cannot see issues on the project
      assertThat(orchestrator.getServer().wsClient(withoutBrowsePermission, "password").issueClient().find(
        IssueQuery.create().componentRoots("sample")).list()).isEmpty();

      // Without user permission, a user cannot see issues on the project
      assertThat(orchestrator.getServer().wsClient(withBrowsePermission, "password").issueClient().find(
        IssueQuery.create().componentRoots("sample")).list()).isNotEmpty();

    } finally {
      client.userClient().deactivate(withBrowsePermission);
      client.userClient().deactivate(withoutBrowsePermission);
    }
  }

  /**
   * SONAR-4839
   */
  @Test
  public void need_user_permission_on_project_to_see_issue_changelog() {
    SonarClient client = orchestrator.getServer().adminWsClient();
    Issue issue = client.issueClient().find(IssueQuery.create()).list().get(0);
    client.issueClient().assign(issue.key(), "admin");

    String withBrowsePermission = "with-browse-permission";
    ;
    String withoutBrowsePermission = "without-browse-permission";

    try {
      client.userClient().create(UserParameters.create().login(withBrowsePermission).name(withBrowsePermission)
        .password("password").passwordConfirmation("password"));
      client.permissionClient().addPermission(PermissionParameters.create().user(withBrowsePermission).component("sample").permission("user"));

      client.userClient().create(UserParameters.create().login(withoutBrowsePermission).name(withoutBrowsePermission)
        .password("password").passwordConfirmation("password"));
      // By default, it's the group anyone that have the permission user, it would be better to remove all groups on this permission
      client.permissionClient().removePermission(PermissionParameters.create().group("anyone").component("sample").permission("user"));

      // Without user permission, a user cannot see issue changelog on the project
      try {
        orchestrator.getServer().wsClient(withoutBrowsePermission, "password").issueClient().changes(issue.key());
        fail();
      } catch (Exception e) {
        assertThat(e).isInstanceOf(HttpException.class).describedAs("404");
      }

      // Without user permission, a user cannot see issues on the project
      assertThat(orchestrator.getServer().wsClient(withBrowsePermission, "password").issueClient().changes(issue.key())).isNotEmpty();

    } finally {
      client.userClient().deactivate(withBrowsePermission);
      client.userClient().deactivate(withoutBrowsePermission);
    }
  }

  /**
   * SONAR-2447
   */
  @Test
  public void need_administer_issue_permission_on_project_to_set_severity() {
    SonarClient client = orchestrator.getServer().adminWsClient();
    Issue issueOnSample = client.issueClient().find(IssueQuery.create().componentRoots("sample")).list().get(0);
    Issue issueOnSample2 = client.issueClient().find(IssueQuery.create().componentRoots("sample2")).list().get(0);

    String user = "user";

    try {
      client.userClient().create(UserParameters.create().login(user).name(user).password("password").passwordConfirmation("password"));
      client.permissionClient().addPermission(PermissionParameters.create().user(user).component("sample").permission("issueadmin"));

      // Without issue admin permission, a user cannot set severity on the issue
      try {
        orchestrator.getServer().wsClient(user, "password").issueClient().setSeverity(issueOnSample2.key(), "BLOCKER");
        fail();
      } catch (Exception e) {
        assertThat(e).isInstanceOf(HttpException.class).describedAs("404");
      }

      // With issue admin permission, a user can set severity on the issue
      assertThat(orchestrator.getServer().wsClient(user, "password").issueClient().setSeverity(issueOnSample.key(), "BLOCKER").severity()).isEqualTo("BLOCKER");

    } finally {
      client.userClient().deactivate(user);
    }
  }

  /**
   * SONAR-2447
   */
  @Test
  public void need_administer_issue_permission_on_project_to_flag_as_false_positive() {
    SonarClient client = orchestrator.getServer().adminWsClient();
    Issue issueOnSample = client.issueClient().find(IssueQuery.create().componentRoots("sample")).list().get(0);
    Issue issueOnSample2 = client.issueClient().find(IssueQuery.create().componentRoots("sample2")).list().get(0);

    String user = "user";

    try {
      client.userClient().create(UserParameters.create().login(user).name(user).password("password").passwordConfirmation("password"));
      client.permissionClient().addPermission(PermissionParameters.create().user(user).component("sample").permission("issueadmin"));

      // Without issue admin permission, a user cannot flag an issue as false positive
      try {
        orchestrator.getServer().wsClient(user, "password").issueClient().doTransition(issueOnSample2.key(), "falsepositive");
        fail();
      } catch (Exception e) {
        assertThat(e).isInstanceOf(HttpException.class).describedAs("404");
      }

      // With issue admin permission, a user can flag an issue as false positive
      assertThat(orchestrator.getServer().wsClient(user, "password").issueClient().doTransition(issueOnSample.key(), "falsepositive").status()).isEqualTo("RESOLVED");

    } finally {
      client.userClient().deactivate(user);
    }
  }

  /**
   * SONAR-2447
   */
  @Test
  public void need_administer_issue_permission_on_project_to_bulk_change_severity_and_false_positive() {
    SonarClient client = orchestrator.getServer().adminWsClient();
    Issue issueOnSample = client.issueClient().find(IssueQuery.create().componentRoots("sample")).list().get(0);
    Issue issueOnSample2 = client.issueClient().find(IssueQuery.create().componentRoots("sample2")).list().get(0);

    String user = "user";

    try {
      client.userClient().create(UserParameters.create().login(user).name(user).password("password").passwordConfirmation("password"));
      client.permissionClient().addPermission(PermissionParameters.create().user(user).component("sample").permission("issueadmin"));

      BulkChange bulkChange = orchestrator.getServer().wsClient(user, "password").issueClient().bulkChange(
        BulkChangeQuery.create().issues(issueOnSample.key(), issueOnSample2.key())
          .actions("set_severity", "do_transition")
          .actionParameter("do_transition", "transition", "falsepositive")
          .actionParameter("set_severity", "severity", "BLOCKER"));

      assertThat(bulkChange.totalIssuesChanged()).isEqualTo(1);
      assertThat(bulkChange.totalIssuesNotChanged()).isEqualTo(1);

    } finally {
      client.userClient().deactivate(user);
    }
  }

  /**
   * SONAR-2447
   */
  @Test
  public void need_administer_issue_permission_to_see_set_severity_and_false_positive_from_issue_detail() {
    SonarClient client = orchestrator.getServer().adminWsClient();

    String withoutIssueAdminPermission = "without-issue-admin-permission";

    try {

      client.userClient().create(UserParameters.create().login(withoutIssueAdminPermission).name(withoutIssueAdminPermission)
        .password("password").passwordConfirmation("password"));
      client.permissionClient().addPermission(PermissionParameters.create().user(withoutIssueAdminPermission).component("sample").permission("codeviewer"));
      client.permissionClient().addPermission(PermissionParameters.create().user(withoutIssueAdminPermission).component("sample").permission("user"));

      orchestrator.executeSelenese(Selenese.builder().setHtmlTestsInClasspath("need_administer_issue_permission_to_see_set_severity_and_false_positive_from_issue_detail",
        "/selenium/permission/issue-permissions/without-issue-admin-permission-some-actions-from-issue-detail-are-hidden.html"
        ).build());

    } finally {
      client.userClient().deactivate(withoutIssueAdminPermission);
    }
  }
}
