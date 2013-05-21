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

package com.evolveum.midpoint.model.security.api;

import com.evolveum.midpoint.common.security.MidPointPrincipal;

/**
 * Service that exposes security functions for GUI and other spring-security-enabled authentication front-ends. 
 *
 * @author lazyman
 * @author Igor Farinic
 */
public interface UserDetailsService {
    
    String DOT_CLASS = UserDetailsService.class.getName() + ".";
    String OPERATION_GET_USER = DOT_CLASS + "getUser";
    String OPERATION_UPDATE_USER = DOT_CLASS + "updateUser";

    public MidPointPrincipal getUser(String principal);

    public void updateUser(MidPointPrincipal user);
}
