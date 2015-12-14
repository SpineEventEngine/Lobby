/*
 * Copyright 2015, TeamDev Ltd. All rights reserved.
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package sample.lobby.registration.projection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.spine3.server.storage.memory.InMemoryStorageFactory;
import org.spine3.util.Identifiers;
import sample.lobby.contracts.common.ConferenceId;

import static org.junit.Assert.assertEquals;
import static sample.lobby.registration.projection.ConferenceProjectionShould.buildBoundedContext;

/**
 * @author Alexander Litus
 */
@SuppressWarnings("InstanceMethodNamingConvention")
public class ConferenceProjectionRepositoryShould {

    private final ConferenceProjectionRepository repository = new ConferenceProjectionRepository(buildBoundedContext());
    private final ConferenceId id = ConferenceId.newBuilder().setUuid(Identifiers.newUuid()).build();

    @Before
    public void setUpTest() {
        repository.assignStorage(InMemoryStorageFactory.getInstance().createEntityStorage(null));
    }

    @After
    public void tearDownTest() {
        repository.assignStorage(null);
    }

    @Test
    public void store_and_load_projection() {
        final ConferenceProjection expected = new ConferenceProjection(id);

        repository.store(expected);

        final ConferenceProjection actual = repository.load(id);
        assertEquals(expected.getState(), actual.getState());
    }
}