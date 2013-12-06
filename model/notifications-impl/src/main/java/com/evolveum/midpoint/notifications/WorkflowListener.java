/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

package com.evolveum.midpoint.notifications;

import com.evolveum.midpoint.notifications.api.NotificationManager;
import com.evolveum.midpoint.notifications.api.events.WorkItemEvent;
import com.evolveum.midpoint.notifications.api.events.WorkflowEvent;
import com.evolveum.midpoint.notifications.api.events.WorkflowProcessEvent;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.LightweightIdentifierGenerator;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.api.ProcessListener;
import com.evolveum.midpoint.wf.api.WorkItemListener;
import com.evolveum.midpoint.wf.api.WorkflowService;
import com.evolveum.midpoint.wf.processes.CommonProcessVariableNames;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBException;
import java.util.Map;

/**
 * @author mederly
 */
@Component
public class WorkflowListener implements ProcessListener, WorkItemListener {

    private static final Trace LOGGER = TraceManager.getTrace(WorkflowListener.class);

    //private static final String DOT_CLASS = WorkflowListener.class.getName() + ".";

    @Autowired
    private LightweightIdentifierGenerator lightweightIdentifierGenerator;

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private PrismContext prismContext;

    @Autowired
    @Qualifier("cacheRepositoryService")
    private transient RepositoryService cacheRepositoryService;

    @Autowired
    private NotificationsUtil notificationsUtil;

    // WorkflowService is not required, because e.g. within model-test and model-intest we have no workflows present
    // However, during normal operation, it is expected to be present
    @Autowired(required = false)
    private WorkflowService workflowService;

    @PostConstruct
    public void init() {
        if (workflowService != null) {
            workflowService.registerProcessListener(this);
            workflowService.registerWorkItemListener(this);
        } else {
            LOGGER.warn("WorkflowService not present, notifications for workflows will not be enabled.");
        }
    }

    @Override
    public void onProcessInstanceStart(String instanceName, Map<String, Object> variables, OperationResult result) {
        WorkflowProcessEvent event = createWorkflowProcessEvent(instanceName, variables, ChangeType.ADD, null, result);
        processEvent(event, result);
    }

    @Override
    public void onProcessInstanceEnd(String instanceName, Map<String, Object> variables, String decision, OperationResult result) {
        WorkflowProcessEvent event = createWorkflowProcessEvent(instanceName, variables, ChangeType.DELETE, decision, result);
        processEvent(event, result);
    }

    @Override
    public void onWorkItemCreation(String workItemName, String assigneeOid, String processInstanceName, Map<String, Object> processVariables) {
        WorkItemEvent event = createWorkItemEvent(workItemName, assigneeOid, processInstanceName, processVariables, ChangeType.ADD, null);
        processEvent(event);
    }

    @Override
    public void onWorkItemCompletion(String workItemName, String assigneeOid, String processInstanceName, Map<String, Object> processVariables, String decision) {
        WorkItemEvent event = createWorkItemEvent(workItemName, assigneeOid, processInstanceName, processVariables, ChangeType.DELETE, decision);
        processEvent(event);
    }

    private WorkflowProcessEvent createWorkflowProcessEvent(String instanceName, Map<String, Object> variables, ChangeType changeType, String decision, OperationResult result) {
        WorkflowProcessEvent event = new WorkflowProcessEvent(lightweightIdentifierGenerator, changeType);
        fillInEvent(event, instanceName, variables, decision, result);
        return event;
    }

    private void fillInEvent(WorkflowEvent event, String instanceName, Map<String, Object> variables, String decision, OperationResult result) {
        event.setProcessName(instanceName);
        event.setOperationStatusCustom(decision);
        event.setVariables(variables);

        String objectXml = (String) variables.get(CommonProcessVariableNames.VARIABLE_MIDPOINT_OBJECT_TO_BE_ADDED);

        if (objectXml != null) {
            PrismObject<? extends ObjectType> object = null;
            Exception failReason = null;
            try {
                object = prismContext.getPrismJaxbProcessor().unmarshalObject(objectXml, ObjectType.class).asPrismObject();
            } catch (JAXBException e) {
                failReason = e;
            } catch (SchemaException e) {
                failReason = e;
            }

            if (failReason != null) {
                result.recordPartialError("Couldn't resolve requestee for a workflow notification", failReason);
            } else {
                ObjectType objectType = object.asObjectable();
                if (objectType instanceof UserType) {
                    event.setRequestee(new SimpleObjectRefImpl(notificationsUtil, objectType));
                }
            }
        } else {
            event.setRequestee(new SimpleObjectRefImpl(
                    notificationsUtil, (String) variables.get(CommonProcessVariableNames.VARIABLE_MIDPOINT_OBJECT_OID)));
        }

        event.setRequester(new SimpleObjectRefImpl(
                notificationsUtil,
                (String) variables.get(CommonProcessVariableNames.VARIABLE_MIDPOINT_REQUESTER_OID)));
    }

    private WorkItemEvent createWorkItemEvent(String workItemName, String assigneeOid, String processInstanceName, Map<String, Object> processVariables, ChangeType changeType, String decision) {
        WorkItemEvent event = new WorkItemEvent(lightweightIdentifierGenerator, changeType);
        event.setWorkItemName(workItemName);
        event.setAssignee(new SimpleObjectRefImpl(notificationsUtil, assigneeOid));
        fillInEvent(event, processInstanceName, processVariables, decision, new OperationResult("dummy"));
        return event;

    }

    private void processEvent(WorkflowEvent event, OperationResult result) {
        try {
            notificationManager.processEvent(event);
        } catch (RuntimeException e) {
            result.recordFatalError("An unexpected exception occurred when preparing and sending notifications: " + e.getMessage(), e);
            LoggingUtils.logException(LOGGER, "An unexpected exception occurred when preparing and sending notifications: " + e.getMessage(), e);
        }

        // todo work correctly with operationResult (in whole notification module)
        if (result.isUnknown()) {
            result.computeStatus();
        }
        result.recordSuccessIfUnknown();
    }

    private void processEvent(WorkflowEvent event) {
        try {
            notificationManager.processEvent(event);
        } catch (RuntimeException e) {
            LoggingUtils.logException(LOGGER, "An unexpected exception occurred when preparing and sending notifications: " + e.getMessage(), e);
        }
    }

}