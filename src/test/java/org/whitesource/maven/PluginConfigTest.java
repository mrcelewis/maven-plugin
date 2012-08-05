/**
 * Copyright (C) 2011 White Source Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.maven;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Edo.Shor
 */
public class PluginConfigTest {

    @Test
    public void testMatchPattern() {
        String regex = "org.whitesource:*".replace(".", "\\.").replace("*", ".*");
        assertTrue("org.whitesource:agent:1.0.0".matches(regex));
        assertFalse("orgawhitesource:agent:1.0.0".matches(regex));
        assertFalse("org.whitesource.agent:1.0.0".matches(regex));

        regex = "*white*:*:1.2".replace(".", "\\.").replace("*", ".*");
        assertTrue("org.whitesource:agent:1.2".matches(regex));
        assertFalse("orgawhitesource:agent:1.0.0".matches(regex));
        assertFalse("com.whitesource.agent:1.0.0".matches(regex));

    }
}
