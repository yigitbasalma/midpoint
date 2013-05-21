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
package com.evolveum.midpoint.model.lens;

import com.evolveum.midpoint.xml.ns._public.common.common_2a.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;

/**
 * @author semancik
 *
 */
public class AssignmentPathSegment {
	
	private AssignmentType assignmentType;
	private Assignment evaluatedAssignment;
	private ObjectType target;
	
	AssignmentPathSegment(AssignmentType assignmentType, ObjectType target) {
		super();
		this.assignmentType = assignmentType;
		this.target = target;
	}

	public AssignmentType getAssignmentType() {
		return assignmentType;
	}

	public void setAssignmentType(AssignmentType assignmentType) {
		this.assignmentType = assignmentType;
	}

	public Assignment getEvaluatedAssignment() {
		return evaluatedAssignment;
	}

	public void setEvaluatedAssignment(Assignment evaluatedAssignment) {
		this.evaluatedAssignment = evaluatedAssignment;
	}

	public ObjectType getTarget() {
		return target;
	}

	public void setTarget(ObjectType target) {
		this.target = target;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((assignmentType == null) ? 0 : assignmentType.hashCode());
		result = prime * result + ((target == null) ? 0 : target.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AssignmentPathSegment other = (AssignmentPathSegment) obj;
		if (assignmentType == null) {
			if (other.assignmentType != null)
				return false;
		} else if (!assignmentType.equals(other.assignmentType))
			return false;
		if (target == null) {
			if (other.target != null)
				return false;
		} else if (!target.equals(other.target))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "AssignmentPathSegment(" + assignmentType + " -> " + target + ")";
	}
	
}
