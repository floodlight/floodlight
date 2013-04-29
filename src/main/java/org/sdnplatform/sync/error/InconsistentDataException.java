/*
 * Copyright 2008-2009 LinkedIn, Inc
 * Copyright 2013 Big Switch Networks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.sdnplatform.sync.error;

import java.util.List;

/**
 * Thrown when the inconsistency resolver fails to resolve down to a single
 * value
 */
public class InconsistentDataException extends SyncException {

    private static final long serialVersionUID = 1050277622160468516L;
    List<?> unresolvedVersions;

    public InconsistentDataException(String message, List<?> versions) {
        super(message);
        this.unresolvedVersions = versions;
    }

    public List<?> getUnresolvedVersions() {
        return unresolvedVersions;
    }
    
    @Override
    public ErrorType getErrorType() {
        return ErrorType.INCONSISTENT_DATA;
    }

}
