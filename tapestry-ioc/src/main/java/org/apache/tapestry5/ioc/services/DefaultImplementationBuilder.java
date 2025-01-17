// Copyright 2006, 2011 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry5.ioc.services;

/**
 * Creates default implementatons of a class.
 *
 * @see org.apache.tapestry5.ioc.services.ClassFab#addNoOpMethod(MethodSignature)
 */
public interface DefaultImplementationBuilder
{
    /**
     * Creates a new implementation of the provided interface. Each method in the interface will be implemented as a
     * noop method. The method will ignore any parameters and return null, or 0, or false (or return nothing if the
     * method is void).
     *
     * @param <S>
     * @param serviceInterface
     * @return implementation of service interface
     */
    <S> S createDefaultImplementation(Class<S> serviceInterface);
}
