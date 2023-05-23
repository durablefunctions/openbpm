/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.test.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class modeling the corresponding entity removal of Domain Classes.
 */
public final class Removable {

  private static final Logger LOG = LoggerFactory.getLogger(Removable.class);

  private final ProcessEngine engine;
  private final Map<Class<?>, ThrowingRunnable> mappings;

  /**
   * New Domain Classes & the deletion of their respective Entities goes here.
   */
  private Removable(ProcessEngine engine) {
    Map<Class<?>, ThrowingRunnable> mappings = new HashMap<>();

    mappings.put(Task.class, this::removeAllTasks);
    mappings.put(ProcessInstance.class, this::removeAllProcessInstances);
    mappings.put(Deployment.class, this::removeAllDeployments);

    // Add here new mappings with [class - associated remove method]

    this.engine = engine;
    this.mappings = mappings;
  }

  /**
   * Static Creation method.
   *
   * @param engineTestRule the process engine test rule, non-null.
   * @return the {@link Removable}
   */
  public static Removable of(ProcessEngineTestRule engineTestRule) {
    Objects.requireNonNull(engineTestRule);

    return new Removable(engineTestRule.processEngineRule.getProcessEngine());
  }

  /**
   * Removes the associated mapped entities from the db for the given class.
   *
   * @param clazz the given class to delete associated entities for
   * @throws Exception in case anything fails during the process of deletion
   */
  public void remove(Class<?> clazz) throws Exception {
    Objects.requireNonNull(clazz, "remove does not accept null arguments");

    ThrowingRunnable runnable = mappings.get(clazz);

    if (runnable == null) {
      throw new UnsupportedOperationException("class " + clazz.getName() + " is not supported yet for Removal");
    }

    runnable.execute();
  }

  /**
   * Removes the associated mapped entities from the db for the given classes.
   *
   * @param classes the given classes to delete associated entities for
   * @throws Exception in case anything fails during the process of deletion for any of the classes
   */
  public void remove(Class<?>[] classes) throws Exception {
    Objects.requireNonNull(classes, "remove does not accept null arguments");

    for (Class<?> clazz : classes) {
      remove(clazz);
    }
  }

  /**
   * Removes associated mapped entities for all known classes.
   *
   * @throws Exception in case anything fails during the process of deletion for any of the classes
   */
  public void removeAll() throws Exception {
    for (Map.Entry<Class<?>, ThrowingRunnable> entry : mappings.entrySet()) {
      ThrowingRunnable runnable = entry.getValue();
      runnable.execute();
    }
  }

  private void removeAllTasks() {
    try {
      TaskService taskService = engine.getTaskService();
      List<Task> tasks = taskService.createTaskQuery().list();
      for (Task task : tasks) {
        LOG.debug("deleteTask with taskId: {}", task.getId());
        taskService.deleteTask(task.getId(), true);
      }
    } catch (Exception e) {
      throw new EntityRemoveException(e);
    }
  }

  private void removeAllDeployments() {
    RepositoryService repositoryService = engine.getRepositoryService();
    for (Deployment deployment : engine.getRepositoryService().createDeploymentQuery().list()) {
      repositoryService.deleteDeployment(deployment.getId(), true);
    }
  }

  private void removeAllProcessInstances() {
    try {
      RuntimeService runtimeService = engine.getRuntimeService();
      for (ProcessInstance processInstance : runtimeService.createProcessInstanceQuery().list()) {
        runtimeService.deleteProcessInstance(processInstance.getId(), "test ended", true);
      }
    } catch (Exception e) {
      throw new EntityRemoveException(e);
    }
  }
}

/**
 * Exception thrown if the mapped entities to be deleted for a class fail to be removed.
 */
class EntityRemoveException extends RuntimeException {
  public EntityRemoveException(Exception e) {
    super(e);
  }
}

/**
 * Functional interface used locally to pass functions that can throw exceptions as arguments.
 */
@FunctionalInterface
interface ThrowingRunnable {
  void execute() throws Exception;
}