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

import com.google.protobuf.Message;
import com.google.protobuf.util.TimeUtil;
import io.grpc.stub.StreamObserver;
import org.spine3.base.Event;
import org.spine3.base.EventContext;
import org.spine3.base.EventId;
import org.spine3.base.Identifiers;
import org.spine3.protobuf.Messages;
import org.spine3.samples.lobby.common.ConferenceId;
import org.spine3.samples.lobby.common.util.RandomPasswordGenerator;
import org.spine3.samples.lobby.conference.ConferenceInfo;
import org.spine3.samples.lobby.conference.ConferenceServiceGrpc.ConferenceService;
import org.spine3.samples.lobby.conference.CreateConferenceResponse;
import org.spine3.samples.lobby.conference.EditableConferenceInfo;
import org.spine3.samples.lobby.conference.FindConferenceByIDRequest;
import org.spine3.samples.lobby.conference.FindConferenceRequest;
import org.spine3.samples.lobby.conference.PublishConferenceRequest;
import org.spine3.samples.lobby.conference.PublishConferenceResponse;
import org.spine3.samples.lobby.conference.UnpublishConferenceRequest;
import org.spine3.samples.lobby.conference.UnpublishConferenceResponse;
import org.spine3.samples.lobby.conference.UpdateConferenceResponse;
import org.spine3.samples.lobby.conference.contracts.Conference;
import org.spine3.samples.sample.lobby.conference.contracts.ConferenceCreated;
import org.spine3.samples.sample.lobby.conference.contracts.ConferencePublished;
import org.spine3.samples.sample.lobby.conference.contracts.ConferenceUnpublished;
import org.spine3.samples.sample.lobby.conference.contracts.ConferenceUpdated;
import org.spine3.server.BoundedContext;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.spine.samples.lobby.conference.EventFactory.*;
import static org.spine3.base.Events.createEvent;

/**
 * @author andrii.loboda
 */
public class ConferenceServiceImpl implements ConferenceService {


    private final BoundedContext boundedContext;
    private final ConferenceRepository conferenceRepository;


    public ConferenceServiceImpl(BoundedContext boundedContext, ConferenceRepository conferenceRepository) {
        this.boundedContext = boundedContext;
        this.conferenceRepository = conferenceRepository;
    }

    @Override
    public void createConference(ConferenceInfo conferenceToCreate, StreamObserver<CreateConferenceResponse> responseObserver) {

        final String accessCode = generateAccessCode();
        final Conference conference = asConference(conferenceToCreate);
        final Conference conferenceToPersist = conference.toBuilder()
                                                         .setAccessCode(accessCode)
                                                         .build();
        conferenceRepository.store(conferenceToPersist);

        final ConferenceCreated conferenceCreatedEvent = conferenceCreated(conference);
        postEvents(conference, conferenceCreatedEvent);

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
    public void findConferenceByID(FindConferenceByIDRequest request, StreamObserver<Conference> responseObserver) {
        final Conference conference = conferenceRepository.load(request.getId());

        responseObserver.onNext(conference);
        responseObserver.onCompleted();
    }


    @Override
    public void updateConference(EditableConferenceInfo request, StreamObserver<UpdateConferenceResponse> responseObserver) {
        final Conference existingConference = conferenceRepository.load(request.getId());

        checkNotNull(existingConference, "Can't find the conference with specified id: " + request.getId());

        final Conference conference = updateFromConferenceInfo(existingConference, request);
        conferenceRepository.store(conference);

        final ConferenceUpdated conferenceUpdatedEvent = conferenceUpdated(conference);
        postEvents(conference, conferenceUpdatedEvent);

        final UpdateConferenceResponse response = UpdateConferenceResponse.newBuilder()
                                                                          .setId(conference.getId())
                                                                          .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private static Conference updateFromConferenceInfo(Conference target, EditableConferenceInfo info) {
        return target.toBuilder()
                     .setName(info.getName())
                     .build();
    }

    @Override
    public void publish(PublishConferenceRequest request, StreamObserver<PublishConferenceResponse> responseObserver) {
        final Conference conference = conferenceRepository.load(request.getId());

        checkNotNull(conference, "No conference found");
        checkState(!conference.getIsPublished(), "Conference is already published");

        final Conference publishedConference = conference.toBuilder()
                                                         .setIsPublished(true)
                                                         .build();

        conferenceRepository.store(publishedConference);

        final ConferencePublished conferencePublishedEvent = conferencePublished(publishedConference);
        postEvents(publishedConference, conferencePublishedEvent);

        final PublishConferenceResponse response = PublishConferenceResponse.newBuilder()
                                                                            .setId(publishedConference.getId())
                                                                            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();


    }

    @Override
    public void unPublish(UnpublishConferenceRequest request, StreamObserver<UnpublishConferenceResponse> responseObserver) {
        final Conference conference = conferenceRepository.load(request.getId());

        checkNotNull(conference, "No conference found with id: %s", conference.getId());
        checkState(conference.getIsPublished(), "Conference is already unpublished with ID: %s", conference.getId());

        final Conference unpublishedConference = conference.toBuilder()
                                                           .setIsPublished(false)
                                                           .build();

        conferenceRepository.store(unpublishedConference);

        final ConferenceUnpublished conferenceUnpublishedEvent = conferenceUnPublished(unpublishedConference);
        postEvents(unpublishedConference, conferenceUnpublishedEvent);

        final UnpublishConferenceResponse response = UnpublishConferenceResponse.newBuilder()
                                                                                .setId(unpublishedConference.getId())
                                                                                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();

    }


    private void postEvents(Conference conference, Message... messages) {

        final EventContext context = createConferenceEventContext(conference.getId());

        for (Message message : messages) {
            final Event event = createEvent(message, context);
            boundedContext.getEventBus()
                          .post(event);
        }
    }

    private static EventContext createConferenceEventContext(ConferenceId conferenceId) {

        final EventId eventIDMessage = EventId.newBuilder()
                                              .setUuid(Identifiers.newUuid())
                                              .build();

        final EventContext eventContext = EventContext.newBuilder()
                                                      .setEventId(eventIDMessage)
                                                      .setProducerId(Messages.toAny(conferenceId))
                                                      .setTimestamp(TimeUtil.getCurrentTime())
                                                      .build();
        return eventContext;
    }

    private static String generateAccessCode() {
        return RandomPasswordGenerator.generate(6);
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
