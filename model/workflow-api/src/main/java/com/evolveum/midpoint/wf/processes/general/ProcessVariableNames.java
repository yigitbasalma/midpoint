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

package com.evolveum.midpoint.wf.processes.general;

/**
 * @author Pavol
 */
public class ProcessVariableNames {

    public static final String APPROVAL_REQUEST = "approvalRequest";            // of type ApprovalRequest
    public static final String APPROVAL_TASK_NAME = "approvalTaskName";

    public static final String ALL_DECISIONS = "allDecisions";

    public static final String LOOP_LEVELS_STOP = "loopLevels_stop";

    public static final String LEVEL = "level";
    public static final String APPROVERS_IN_LEVEL = "approversInLevel";
    public static final String DECISION_LIST = "decisionList";
    public static final String LOOP_APPROVERS_IN_LEVEL_STOP = "loopApproversInLevel_stop";
    public static final String WFANSWER = "wfAnswer";
}
