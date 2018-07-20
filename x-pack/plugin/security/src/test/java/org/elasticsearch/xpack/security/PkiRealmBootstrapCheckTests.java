/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security;

import org.elasticsearch.bootstrap.BootstrapCheck;
import org.elasticsearch.bootstrap.BootstrapContext;
import org.elasticsearch.common.settings.MockSecureSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.TestEnvironment;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.core.security.authc.pki.PkiRealmSettings;
import org.elasticsearch.xpack.core.ssl.SSLService;
import org.hamcrest.Matchers;

public class PkiRealmBootstrapCheckTests extends ESTestCase {

    public void testPkiRealmBootstrapDefault() throws Exception {
        final Settings settings = Settings.EMPTY;
        final Environment env = TestEnvironment.newEnvironment(Settings.builder().put("path.home", createTempDir()).build());
        assertFalse(runCheck(settings, env).isFailure());
    }

    public void testBootstrapCheckWithPkiRealm() throws Exception {
        Settings settings = Settings.builder()
                .put("xpack.security.authc.realms.test_pki.type", PkiRealmSettings.TYPE)
                .put("path.home", createTempDir())
                .build();
        Environment env = TestEnvironment.newEnvironment(settings);
        assertTrue(runCheck(settings, env).isFailure());

        // enable transport tls
        settings = Settings.builder().put(settings)
                .put("xpack.security.transport.ssl.enabled", true)
                .build();
        assertFalse(runCheck(settings, env).isFailure());

        // disable client auth default
        settings = Settings.builder().put(settings)
                .put("xpack.ssl.client_authentication", "none")
                .build();
        env = TestEnvironment.newEnvironment(settings);
        assertTrue(runCheck(settings, env).isFailure());

        // enable ssl for http
        settings = Settings.builder().put(settings)
                .put("xpack.security.http.ssl.enabled", true)
                .build();
        env = TestEnvironment.newEnvironment(settings);
        assertTrue(runCheck(settings, env).isFailure());

        // enable client auth for http
        settings = Settings.builder().put(settings)
                .put("xpack.security.http.ssl.client_authentication", randomFrom("required", "optional"))
                .build();
        env = TestEnvironment.newEnvironment(settings);
        assertFalse(runCheck(settings, env).isFailure());

        // disable http ssl
        settings = Settings.builder().put(settings)
                .put("xpack.security.http.ssl.enabled", false)
                .build();
        env = TestEnvironment.newEnvironment(settings);
        assertTrue(runCheck(settings, env).isFailure());

        // set transport client auth
        settings = Settings.builder().put(settings)
                .put("xpack.security.transport.client_authentication", randomFrom("required", "optional"))
                .build();
        env = TestEnvironment.newEnvironment(settings);
        assertTrue(runCheck(settings, env).isFailure());

        // test with transport profile
        settings = Settings.builder().put(settings)
                .put("xpack.security.transport.client_authentication", "none")
                .put("transport.profiles.foo.xpack.security.ssl.client_authentication", randomFrom("required", "optional"))
                .build();
        env = TestEnvironment.newEnvironment(settings);
        assertFalse(runCheck(settings, env).isFailure());
    }

    private BootstrapCheck.BootstrapCheckResult runCheck(Settings settings, Environment env) throws Exception {
        return new PkiRealmBootstrapCheck(new SSLService(settings, env)).check(new BootstrapContext(settings, null));
    }

    public void testBootstrapCheckWithDisabledRealm() throws Exception {
        Settings settings = Settings.builder()
                .put("xpack.security.authc.realms.test_pki.type", PkiRealmSettings.TYPE)
                .put("xpack.security.authc.realms.test_pki.enabled", false)
                .put("xpack.ssl.client_authentication", "none")
                .put("path.home", createTempDir())
                .build();
        Environment env = TestEnvironment.newEnvironment(settings);
        assertFalse(runCheck(settings, env).isFailure());
    }

    public void testBootstrapCheckWithClosedSecuredSetting() throws Exception {
        final boolean expectFail = randomBoolean();
        final MockSecureSettings secureSettings = new MockSecureSettings();
        secureSettings.setString("xpack.security.http.ssl.keystore.secure_password", "testnode");
        Settings settings = Settings.builder()
                .put("xpack.security.authc.realms.test_pki.type", PkiRealmSettings.TYPE)
                .put("xpack.security.http.ssl.enabled", true)
                .put("xpack.security.http.ssl.client_authentication", expectFail ? "none" : "optional")
                .put("xpack.security.http.ssl.keystore.path",
                        getDataPath("/org/elasticsearch/xpack/security/transport/ssl/certs/simple/testnode.jks"))
                .put("path.home", createTempDir())
                .setSecureSettings(secureSettings)
                .build();

        Environment env = TestEnvironment.newEnvironment(settings);
        final PkiRealmBootstrapCheck check = new PkiRealmBootstrapCheck(new SSLService(settings, env));
        secureSettings.close();
        assertThat(check.check(new BootstrapContext(settings, null)).isFailure(), Matchers.equalTo(expectFail));
    }
}
