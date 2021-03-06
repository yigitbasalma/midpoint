/*
 * Copyright (c) 2010-2016 Evolveum
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

package com.evolveum.midpoint.wf.impl.activiti.dao;

import com.evolveum.midpoint.model.common.SystemObjectCache;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.util.CloneUtil;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.WfContextUtil;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.security.api.SecurityEnforcer;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.api.WorkflowManager;
import com.evolveum.midpoint.wf.impl.activiti.ActivitiEngine;
import com.evolveum.midpoint.wf.impl.processes.common.ActivitiUtil;
import com.evolveum.midpoint.wf.impl.processes.common.CommonProcessVariableNames;
import com.evolveum.midpoint.wf.impl.processes.itemApproval.MidpointUtil;
import com.evolveum.midpoint.wf.impl.tasks.WfTaskController;
import com.evolveum.midpoint.wf.impl.util.MiscDataUtil;
import com.evolveum.midpoint.wf.impl.util.SingleItemSerializationSafeContainerImpl;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;
import org.activiti.engine.FormService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.task.IdentityLink;
import org.activiti.engine.task.IdentityLinkType;
import org.activiti.engine.task.Task;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.*;
import java.util.stream.Collectors;

import static com.evolveum.midpoint.schema.util.ObjectTypeUtil.toShortString;
import static com.evolveum.midpoint.wf.impl.processes.common.CommonProcessVariableNames.ADDITIONAL_ASSIGNEES_PREFIX;
import static com.evolveum.midpoint.wf.impl.processes.common.CommonProcessVariableNames.ADDITIONAL_ASSIGNEES_SUFFIX;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.WorkItemOperationKindType.DELEGATE;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.WorkItemOperationKindType.ESCALATE;

/**
 * @author mederly
 */

@Component
public class WorkItemManager {

    private static final transient Trace LOGGER = TraceManager.getTrace(WorkItemManager.class);

    @Autowired private ActivitiEngine activitiEngine;
    @Autowired private MiscDataUtil miscDataUtil;
    @Autowired private SecurityEnforcer securityEnforcer;
    @Autowired private SystemObjectCache systemObjectCache;
    @Autowired private PrismContext prismContext;
    @Autowired private WorkItemProvider workItemProvider;
    @Autowired private WfTaskController wfTaskController;
    @Autowired private TaskManager taskManager;

    private static final String DOT_INTERFACE = WorkflowManager.class.getName() + ".";

    private static final String OPERATION_COMPLETE_WORK_ITEM = DOT_INTERFACE + "completeWorkItem";
    private static final String OPERATION_CLAIM_WORK_ITEM = DOT_INTERFACE + "claimWorkItem";
    private static final String OPERATION_RELEASE_WORK_ITEM = DOT_INTERFACE + "releaseWorkItem";
    private static final String OPERATION_DELEGATE_WORK_ITEM = DOT_INTERFACE + "delegateWorkItem";

    public void completeWorkItem(String workItemId, String decision, String comment, ObjectDelta additionalDelta,
			WorkItemEventCauseInformationType causeInformation, OperationResult parentResult)
			throws SecurityViolationException, SchemaException {

        OperationResult result = parentResult.createSubresult(OPERATION_COMPLETE_WORK_ITEM);
        result.addParams(new String[] { "workItemId", "decision", "comment", "additionalDelta" },
				workItemId, decision, comment, additionalDelta);

		try {
			final String userDescription = toShortString(securityEnforcer.getPrincipal().getUser());
			result.addContext("user", userDescription);

			LOGGER.trace("Completing work item {} with decision of {} ['{}'] by {}; cause: {}",
					workItemId, decision, comment, userDescription, causeInformation);

			TaskService taskService = activitiEngine.getTaskService();
			taskService.setVariableLocal(workItemId, CommonProcessVariableNames.VARIABLE_CAUSE,
					new SingleItemSerializationSafeContainerImpl<>(causeInformation, prismContext));

			FormService formService = activitiEngine.getFormService();
			TaskFormData data = activitiEngine.getFormService().getTaskFormData(workItemId);

			WorkItemType workItem = workItemProvider.getWorkItem(workItemId, result);

			if (!miscDataUtil.isAuthorized(workItem, MiscDataUtil.RequestedOperation.COMPLETE)) {
				throw new SecurityViolationException("You are not authorized to complete this work item.");
			}

			final Map<String, String> propertiesToSubmit = new HashMap<>();
			propertiesToSubmit.put(CommonProcessVariableNames.FORM_FIELD_DECISION, decision);
			propertiesToSubmit.put(CommonProcessVariableNames.FORM_FIELD_COMMENT, comment);
			if (additionalDelta != null) {
				ObjectDeltaType objectDeltaType = DeltaConvertor.toObjectDeltaType(additionalDelta);
				String xmlDelta = prismContext.xmlSerializer()
						.serializeRealValue(objectDeltaType, SchemaConstants.T_OBJECT_DELTA);
				propertiesToSubmit.put(CommonProcessVariableNames.FORM_FIELD_ADDITIONAL_DELTA, xmlDelta);
			}

			// we also fill-in the corresponding 'button' property (if there's one that corresponds to the decision)
			for (FormProperty formProperty : data.getFormProperties()) {
				if (formProperty.getId().startsWith(CommonProcessVariableNames.FORM_BUTTON_PREFIX)) {
					boolean value = formProperty.getId().equals(CommonProcessVariableNames.FORM_BUTTON_PREFIX + decision);
					LOGGER.trace("Setting the value of {} to writable property {}", value, formProperty.getId());
					propertiesToSubmit.put(formProperty.getId(), Boolean.toString(value));
				}
			}
			LOGGER.trace("Submitting {} properties", propertiesToSubmit.size());
			//formService.submitTaskFormData(workItemId, propertiesToSubmit);
			Map<String, Object> variables = new HashMap<>(propertiesToSubmit);
			taskService.complete(workItemId, variables, true);
		} catch (SecurityViolationException|SchemaException|RuntimeException e) {
			result.recordFatalError("Couldn't complete the work item " + workItemId + ": " + e.getMessage(), e);
			throw e;
		} finally {
			result.computeStatusIfUnknown();
		}
    }

    public void claimWorkItem(String workItemId, OperationResult parentResult) throws SecurityViolationException, ObjectNotFoundException {
        OperationResult result = parentResult.createSubresult(OPERATION_CLAIM_WORK_ITEM);
        result.addParam("workItemId", workItemId);
		try {
			MidPointPrincipal principal = securityEnforcer.getPrincipal();
			result.addContext("user", toShortString(principal.getUser()));

			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Claiming work item {} by {}", workItemId, toShortString(principal.getUser()));
			}

			TaskService taskService = activitiEngine.getTaskService();
			Task task = taskService.createTaskQuery().taskId(workItemId).singleResult();
			if (task == null) {
				throw new ObjectNotFoundException("The work item does not exist");
			}
			if (task.getAssignee() != null) {
				String desc = task.getAssignee().equals(principal.getOid()) ? "the current" : "another";
				throw new SystemException("The work item is already assigned to "+desc+" user");
			}
			if (!miscDataUtil.isAuthorizedToClaim(task.getId())) {
				throw new SecurityViolationException("You are not authorized to claim the selected work item.");
			}
			taskService.claim(workItemId, principal.getOid());
		} catch (ObjectNotFoundException|SecurityViolationException|RuntimeException e) {
			result.recordFatalError("Couldn't claim the work item " + workItemId + ": " + e.getMessage(), e);
			throw e;
		} finally {
			result.computeStatusIfUnknown();
		}
	}

    public void releaseWorkItem(String workItemId, OperationResult parentResult) throws ObjectNotFoundException, SecurityViolationException {
        OperationResult result = parentResult.createSubresult(OPERATION_RELEASE_WORK_ITEM);
        result.addParam("workItemId", workItemId);
		try {
			MidPointPrincipal principal = securityEnforcer.getPrincipal();
			result.addContext("user", toShortString(principal.getUser()));

			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Releasing work item {} by {}", workItemId, toShortString(principal.getUser()));
			}

            TaskService taskService = activitiEngine.getTaskService();
            Task task = taskService.createTaskQuery().taskId(workItemId).singleResult();
            if (task == null) {
				throw new ObjectNotFoundException("The work item does not exist");
            }
            if (task.getAssignee() == null) {
				throw new SystemException("The work item is not assigned to a user");
            }
            if (!task.getAssignee().equals(principal.getOid())) {
                throw new SystemException("The work item is not assigned to the current user");
            }
            boolean candidateFound = false;
            for (IdentityLink link : taskService.getIdentityLinksForTask(workItemId)) {
                if (IdentityLinkType.CANDIDATE.equals(link.getType())) {
                    candidateFound = true;
                    break;
                }
            }
            if (!candidateFound) {
                throw new SystemException("It has no candidates to be offered to");
            }
            taskService.unclaim(workItemId);
        } catch (ObjectNotFoundException|SecurityViolationException|RuntimeException e) {
            result.recordFatalError("Couldn't release work item " + workItemId + ": " + e.getMessage(), e);
			throw e;
        } finally {
			result.computeStatusIfUnknown();
		}
    }

    // TODO when calling from model API, what should we put into escalationLevelName+DisplayName ?
	// Probably the API should look different. E.g. there could be an "Escalate" button, that would look up the
	// appropriate escalation timed action, and invoke it. We'll solve this when necessary. Until that time, be
	// aware that escalationLevelName/DisplayName are for internal use only.
	public void delegateWorkItem(String workItemId, List<ObjectReferenceType> delegates, WorkItemDelegationMethodType method,
			boolean escalate, String escalationLevelName, String escalationLevelDisplayName,
			Duration newDuration, WorkItemEventCauseInformationType causeInformation,
			OperationResult parentResult)
			throws ObjectNotFoundException, SecurityViolationException, SchemaException {
		OperationResult result = parentResult.createSubresult(OPERATION_DELEGATE_WORK_ITEM);
		result.addParams( new String[]{ "workItemId", "escalate", "escalationLevelName", "escalationLevelDisplayName"},
				workItemId, escalate, escalationLevelName, escalationLevelDisplayName);
		result.addCollectionOfSerializablesAsParam("delegates", delegates);
		try {
			MidPointPrincipal principal = securityEnforcer.getPrincipal();
			result.addContext("user", toShortString(principal.getUser()));

			ObjectReferenceType initiator =
					principal.getUser() != null && (causeInformation == null || causeInformation.getCause() == WorkItemEventCauseType.USER_ACTION) ?
							ObjectTypeUtil.createObjectRef(principal.getUser()) : null;

			LOGGER.trace("Delegating work item {} to {}: escalation={}:{}/{}; cause={}", workItemId, delegates, escalate,
					escalationLevelName, escalationLevelDisplayName, causeInformation);

			WorkItemType workItem = workItemProvider.getWorkItem(workItemId, result);
			if (!miscDataUtil.isAuthorized(workItem, MiscDataUtil.RequestedOperation.DELEGATE)) {
				throw new SecurityViolationException("You are not authorized to delegate this work item.");
			}

			List<ObjectReferenceType> assigneesBefore = CloneUtil.cloneCollectionMembers(workItem.getAssigneeRef());

			WorkItemOperationKindType operationKind = escalate ? ESCALATE : DELEGATE;

			com.evolveum.midpoint.task.api.Task wfTask = taskManager.getTask(workItem.getTaskRef().getOid(), result);
			wfTaskController.notifyWorkItemAllocationChangeCurrentActors(workItem, assigneesBefore, null, operationKind,
					initiator, null, causeInformation, wfTask, result);

			List<ObjectReferenceType> newAssignees;
			if (method == null) {
				method = WorkItemDelegationMethodType.REPLACE_ASSIGNEES;
			}
			switch (method) {
				case ADD_ASSIGNEES: newAssignees = new ArrayList<>(workItem.getAssigneeRef()); break;
				case REPLACE_ASSIGNEES: newAssignees = new ArrayList<>(); break;
				default: throw new UnsupportedOperationException("Delegation method " + method + " is not supported yet.");
			}

			List<ObjectReferenceType> delegatedTo = new ArrayList<>();
			for (ObjectReferenceType delegate : delegates) {
				if (delegate.getType() != null && !QNameUtil.match(UserType.COMPLEX_TYPE, delegate.getType())) {
					throw new IllegalArgumentException("Couldn't use non-user object as a delegate: " + delegate);
				}
				if (delegate.getOid() == null) {
					throw new IllegalArgumentException("Couldn't use no-OID reference as a delegate: " + delegate);
				}
				if (!ObjectTypeUtil.containsOid(newAssignees, delegate.getOid())) {
					newAssignees.add(delegate.clone());
					delegatedTo.add(delegate.clone());
				}
			}

			// don't change the current assignee, if not necessary
			TaskService taskService = activitiEngine.getTaskService();
			Task task = taskService.createTaskQuery().taskId(workItemId).singleResult();
			setNewAssignees(task, newAssignees, taskService);
			Date deadline = task.getDueDate();
			if (newDuration != null) {
				deadline = setNewDuration(task.getId(), newDuration, taskService);
			}

			Map<String, Object> variables = taskService.getVariables(workItemId);
			WorkItemDelegationEventType event;
			int escalationLevel = workItem.getEscalationLevelNumber() != null ? workItem.getEscalationLevelNumber() : 0;
			if (escalate) {
				WorkItemEscalationEventType escEvent = new WorkItemEscalationEventType();
				escEvent.setNewEscalationLevelName(escalationLevelName);
				escEvent.setNewEscalationLevelDisplayName(escalationLevelDisplayName);
				escalationLevel = escalationLevel + 1;
				escEvent.setNewEscalationLevelNumber(escalationLevel);
				taskService.setVariableLocal(workItemId, CommonProcessVariableNames.VARIABLE_ESCALATION_LEVEL_NUMBER, escalationLevel);
				taskService.setVariableLocal(workItemId, CommonProcessVariableNames.VARIABLE_ESCALATION_LEVEL_NAME, escalationLevelName);
				taskService.setVariableLocal(workItemId, CommonProcessVariableNames.VARIABLE_ESCALATION_LEVEL_DISPLAY_NAME, escalationLevelDisplayName);
				event = escEvent;
			} else {
				event = new WorkItemDelegationEventType();
			}
			event.getAssigneeBefore().addAll(assigneesBefore);  // already parent-less
			event.getDelegatedTo().addAll(delegatedTo);
			event.setDelegationMethod(method);		// not null at this moment
			event.setCause(causeInformation);
			ActivitiUtil.fillInWorkItemEvent(event, principal, workItemId, variables, prismContext);
			MidpointUtil.recordEventInTask(event, null, ActivitiUtil.getTaskOid(variables), result);

			ApprovalLevelType level = WfContextUtil.getCurrentApprovalLevel(wfTask.getWorkflowContext());
			MidpointUtil.createTriggersForTimedActions(workItemId, escalationLevel,
					XmlTypeConverter.toDate(workItem.getWorkItemCreatedTimestamp()),
					deadline, wfTask, level.getTimedActions(), result);

			WorkItemType workItemAfter = workItemProvider.getWorkItem(workItemId, result);
			com.evolveum.midpoint.task.api.Task wfTaskAfter = taskManager.getTask(wfTask.getOid(), result);
			wfTaskController.notifyWorkItemAllocationChangeNewActors(workItemAfter, assigneesBefore,
					workItemAfter.getAssigneeRef(), operationKind, initiator, null, causeInformation, wfTaskAfter, result);
		} catch (SecurityViolationException|RuntimeException|ObjectNotFoundException|SchemaException e) {
			result.recordFatalError("Couldn't delegate/escalate work item " + workItemId + ": " + e.getMessage(), e);
			throw e;
		} finally {
			result.computeStatusIfUnknown();
		}
	}

	private Date setNewDuration(String workItemId, @NotNull Duration newDuration, TaskService taskService) {
		XMLGregorianCalendar newDeadline = XmlTypeConverter.createXMLGregorianCalendar(new Date());
		newDeadline.add(newDuration);
		Date dueDate = XmlTypeConverter.toDate(newDeadline);
		taskService.setDueDate(workItemId, dueDate);
		return dueDate;
	}

	private void setNewAssignees(Task task, List<ObjectReferenceType> newAssignees, TaskService taskService) {
		if (task.getAssignee() != null) {
			boolean removed = newAssignees.removeIf(newAssignee -> task.getAssignee().equals(newAssignee.getOid()));
			if (!removed) {
				// we will nominate the first delegate (if present) as a new assignee
				if (!newAssignees.isEmpty()) {
					taskService.setAssignee(task.getId(), newAssignees.get(0).getOid());
					newAssignees.remove(0);
				} else {
					taskService.setAssignee(task.getId(), null);
				}
			}
		}
		String additionalAssigneesAsString = newAssignees.isEmpty() ? null : newAssignees.stream()
				.map(d -> ADDITIONAL_ASSIGNEES_PREFIX + MiscDataUtil.refToString(d) + ADDITIONAL_ASSIGNEES_SUFFIX)
				.collect(Collectors.joining(CommonProcessVariableNames.ADDITIONAL_ASSIGNEES_SEPARATOR));
		taskService.setVariableLocal(task.getId(), CommonProcessVariableNames.VARIABLE_ADDITIONAL_ASSIGNEES, additionalAssigneesAsString);
	}

}
