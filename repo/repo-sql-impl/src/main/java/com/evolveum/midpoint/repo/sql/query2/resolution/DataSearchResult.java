/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.repo.sql.query2.resolution;

import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.repo.sql.query2.definition.JpaDataNodeDefinition;
import com.evolveum.midpoint.repo.sql.query2.definition.JpaLinkDefinition;
import org.apache.commons.lang.Validate;

/**
 * @author mederly
 */
public class DataSearchResult<T extends JpaDataNodeDefinition> {
    private JpaLinkDefinition<T> linkDefinition;
    private ItemPath remainder;                             // what has remained unresolved of the original search path

    public DataSearchResult(JpaLinkDefinition<T> linkDefinition, ItemPath remainder) {
        Validate.notNull(linkDefinition, "linkDefinition");
        this.linkDefinition = linkDefinition;
        this.remainder = remainder;
    }

    public JpaLinkDefinition<T> getLinkDefinition() {
        return linkDefinition;
    }

    public ItemPath getRemainder() {
        return remainder;
    }

    public boolean isComplete() {
        return ItemPath.isNullOrEmpty(remainder);
    }

    public T getTargetDefinition() {
        return linkDefinition.getTargetDefinition();
    }
}
