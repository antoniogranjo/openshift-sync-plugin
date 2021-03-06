/**
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.jenkins.openshiftsync;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Queue;
import io.fabric8.openshift.api.model.BuildRequestBuilder;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import java.util.List;

import static io.fabric8.jenkins.openshiftsync.BuildSyncRunListener.joinPaths;
import static io.fabric8.jenkins.openshiftsync.OpenShiftUtils.getJenkinsURL;
import static io.fabric8.jenkins.openshiftsync.OpenShiftUtils.getOpenShiftClient;

@Extension
public class BuildDecisionHandler extends Queue.QueueDecisionHandler {

  @Override
  public boolean shouldSchedule(Queue.Task p, List<Action> actions) {
    if (p instanceof WorkflowJob && !isOpenShiftBuildCause(actions)) {
      WorkflowJob wj = (WorkflowJob) p;
      BuildConfigProjectProperty buildConfigProjectProperty = wj.getProperty(BuildConfigProjectProperty.class);
      if (buildConfigProjectProperty != null
        && StringUtils.isNotBlank(buildConfigProjectProperty.getNamespace())
        && StringUtils.isNotBlank(buildConfigProjectProperty.getName())) {

        String namespace = buildConfigProjectProperty.getNamespace();
        String jobURL = joinPaths(getJenkinsURL(getOpenShiftClient(), namespace), wj.getUrl());

        getOpenShiftClient().buildConfigs()
          .inNamespace(namespace).withName(buildConfigProjectProperty.getName())
          .instantiate(
            new BuildRequestBuilder()
              .withNewMetadata().withName(buildConfigProjectProperty.getName()).and()
              .addNewTriggeredBy().withMessage("Triggered by Jenkins job at " + jobURL).and()
              .build()
          );
        return false;
      }
    }

    return true;
  }

  private boolean isOpenShiftBuildCause(List<Action> actions) {
    for (Action action : actions) {
      if (action instanceof CauseAction) {
        CauseAction causeAction = (CauseAction) action;
        for (Cause cause : causeAction.getCauses()) {
          if (cause instanceof BuildCause) {
            return true;
          }
        }
      }
    }
    return false;
  }

}
