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

package org.spine.samples.lobby.conference;

import com.google.common.collect.ImmutableList;
import io.grpc.stub.StreamObserver;
import org.spine3.base.Event;
import org.spine3.base.EventContext;
import org.spine3.base.Events;
import org.spine3.base.Identifiers;
import org.spine3.samples.lobby.common.ConferenceId;
import org.spine3.samples.lobby.conference.ConferenceInfo;
import org.spine3.samples.lobby.conference.ConferenceServiceGrpc.ConferenceService;
import org.spine3.samples.lobby.conference.CreateConferenceResponse;
import org.spine3.samples.lobby.conference.EditableConferenceInfo;
import org.spine3.samples.lobby.conference.FindConferenceRequest;
import org.spine3.samples.lobby.conference.PublishConferenceRequest;
import org.spine3.samples.lobby.conference.PublishConferenceResponse;
import org.spine3.samples.lobby.conference.UpdateConferenceResponse;
import org.spine3.samples.lobby.conference.contracts.Conference;
import org.spine3.samples.sample.lobby.conference.contracts.ConferenceCreated;
import org.spine3.samples.sample.lobby.conference.contracts.ConferencePublished;
import org.spine3.samples.sample.lobby.conference.contracts.ConferenceUpdated;
import org.spine3.server.BoundedContext;

import java.math.BigInteger;
import java.security.SecureRandom;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author andrii.loboda
 */
public class ConferenceServiceImpl implements ConferenceService {


    private final BoundedContext boundedContext;
    private final ConferenceRepository conferenceRepository;


    public ConferenceServiceImpl() {
        boundedContext = getBoundedContext();
        conferenceRepository = getRepository();
    }


    @Override
    public void createConference(ConferenceInfo conferenceToCreate, StreamObserver<CreateConferenceResponse> responseObserver) {

        final String accessCode = generateAccessCode();
        final Conference conference = asConference(conferenceToCreate);
        final Conference conferenceToPersist = conference.toBuilder()
                                                         .setAccessCode(accessCode)
                                                         .build();
        conferenceRepository.store(conferenceToPersist);

        sendConferenceCreatedEvent(conferenceToPersist);

        final CreateConferenceResponse build = CreateConferenceResponse.newBuilder()
                                                                       .setId(conferenceToPersist.getId())
                                                                       .setAccessCode(accessCode)
                                                                       .build();

        responseObserver.onNext(build);
        responseObserver.onCompleted();
    }


    @Override
    public void findConference(FindConferenceRequest request, StreamObserver<Conference> responseObserver) {
        final Conference conference = conferenceRepository.load(request.getEmailAddress(), request.getAccessCode());

        responseObserver.onNext(conference);
        responseObserver.onCompleted();
    }

    @Override
    public void updateConference(EditableConferenceInfo request, StreamObserver<UpdateConferenceResponse> responseObserver) {
        final Conference existingConference = conferenceRepository.load(request.getId());

        checkNotNull(existingConference, "Can't finde conference with specified id: " + request.getId());

        final Conference updatedConference = updateFromConferenceInfo(existingConference, request);
        conferenceRepository.store(updatedConference);

        sendConferenceUpdatedEvent(updatedConference);
        final UpdateConferenceResponse response = UpdateConferenceResponse.newBuilder()
                                                                          .setId(updatedConference.getId())
                                                                          .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private Conference updateFromConferenceInfo(Conference target, EditableConferenceInfo info) {
        return target.toBuilder()
                     .setName(info.getName())
                     .build();
    }

    @Override
    public void publish(PublishConferenceRequest request, StreamObserver<PublishConferenceResponse> responseObserver) {
        final Conference persistedConference = conferenceRepository.load(request.getId());
        //TODO:2016-04-19:andrii.loboda: handle if no conference to publish
        if (!persistedConference.getIsPublished()) {
            final Conference publishedConference = persistedConference.toBuilder()
                                                                      .setIsPublished(true)
                                                                      .build();

            sendConferencePublishedEvent(publishedConference);
            final PublishConferenceResponse response = PublishConferenceResponse.newBuilder()
                                                                                .setId(persistedConference.getId())
                                                                                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } else {

            //TODO:2016-04-19:andrii.loboda: handle if conference is already published
        }

    }

    protected BoundedContext getBoundedContext() {
        //TODO:2016-04-14:andrii.loboda:  move to singleton and provide event bus, command bus, etc.
        return BoundedContext.newBuilder()
                             .build();
    }

    protected ConferenceRepository getRepository() {
        return new ConferenceRepository();
    }


    private void sendConferenceCreatedEvent(Conference createdConference) {
        final ImmutableList.Builder<Event> result = ImmutableList.builder();
        final ConferenceCreated conferenceCreatedEvent = EventFactory.conferenceCreated(createdConference);
        final EventContext conferenceEventContext = EventUtils.createConferenceEventContext(createdConference.getId());
        result.add(Events.createEvent(conferenceCreatedEvent, conferenceEventContext));

        sendEvents(result.build());
    }

    private void sendConferenceUpdatedEvent(Conference updatedConference) {
        final ImmutableList.Builder<Event> result = ImmutableList.builder();
        final ConferenceUpdated conferenceUpdatedEvent = EventFactory.conferenceUpdated(updatedConference);
        final EventContext conferenceEventContext = EventUtils.createConferenceEventContext(updatedConference.getId());
        result.add(Events.createEvent(conferenceUpdatedEvent, conferenceEventContext));
//TODO:2016-04-19:andrii.loboda: looks weird
        sendEvents(result.build());
    }

    private void sendConferencePublishedEvent(Conference publishedConference) {
        final ImmutableList.Builder<Event> result = ImmutableList.builder();
        final ConferencePublished conferencePublishedEvent = EventFactory.conferencePublished(publishedConference);
        final EventContext conferenceEventContext = EventUtils.createConferenceEventContext(publishedConference.getId());
        result.add(Events.createEvent(conferencePublishedEvent, conferenceEventContext));

        sendEvents(result.build());
    }


    private void sendEvents(Iterable<Event> eventsToSend) {
        for (Event event : eventsToSend) {
            boundedContext.getEventBus()
                          .post(event);
        }
    }

    private static String generateAccessCode() {
        final SecureRandom random = new SecureRandom();
        final int encodingBase = 32;
        final int numBits = 130;
        return new BigInteger(numBits, random).toString(encodingBase)
                                              .substring(0, 6);
    }

    private static Conference asConference(ConferenceInfo info) {
        final ConferenceId conferenceId = ConferenceId.newBuilder()
                                                      .setUuid(Identifiers.newUuid())
                                                      .build();
        return Conference.newBuilder()
                         .setId(conferenceId)
                         .setName(info.getName())
                         .setOwner(info.getOwner())
                         .build();
    }

}
