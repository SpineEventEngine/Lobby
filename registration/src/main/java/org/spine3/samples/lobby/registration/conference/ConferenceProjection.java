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

package org.spine3.samples.lobby.registration.conference;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spine3.base.CommandContext;
import org.spine3.client.CommandRequest;
import org.spine3.eventbus.Subscribe;
import org.spine3.samples.lobby.common.ConferenceId;
import org.spine3.samples.lobby.common.SeatType;
import org.spine3.samples.lobby.common.SeatTypeId;
import org.spine3.samples.lobby.conference.contracts.Conference;
import org.spine3.samples.lobby.registration.seat.availability.AddSeats;
import org.spine3.samples.lobby.registration.seat.availability.RemoveSeats;
import org.spine3.samples.sample.lobby.conference.contracts.*;
import org.spine3.server.BoundedContext;
import org.spine3.server.projection.Projection;

import javax.annotation.Nullable;
import java.util.List;

import static com.google.common.collect.Iterables.filter;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static org.spine3.samples.lobby.registration.util.Seats.newSeatQuantity;
import static org.spine3.util.Commands.newCommandRequest;

/**
 * Holds a structural representation of data extracted from a stream of events related to a conference.
 *
 * <p> Contains the data about the conference for registrants.
 *
 * <p> Also notifies about some of the data changes by sending commands.
 *
 * @author Alexander Litus
 * @see Projection
 */
@SuppressWarnings("TypeMayBeWeakened")
public class ConferenceProjection extends Projection<ConferenceId, Conference> {

    private BoundedContext boundedContext;

    /**
     * Creates a new instance.
     *
     * @param id the ID for the new instance
     * @throws IllegalArgumentException if the ID type is not supported
     */
    public ConferenceProjection(ConferenceId id) {
        super(id);
    }

    public void setBoundedContext(BoundedContext boundedContext) {
        this.boundedContext = boundedContext;
    }

    @Override
    protected Conference getDefaultState() {
        return Conference.getDefaultInstance();
    }

    @Subscribe
    public void on(ConferenceCreated event) {
        final Conference newState = event.getConference();
        incrementState(newState);
    }

    @Subscribe
    public void on(ConferenceUpdated event) {
        final Conference newState = event.getConference();
        incrementState(newState);
    }

    @Subscribe
    public void on(ConferencePublished event) {
        updatePublishingStatus(true);
    }

    @Subscribe
    public void on(ConferenceUnpublished event) {
        updatePublishingStatus(false);
    }

    private void updatePublishingStatus(boolean isPublished) {
        final Conference.Builder conference = getState().toBuilder();
        conference.setIsPublished(isPublished);
        incrementState(conference.build());
    }

    @Subscribe
    public void on(SeatTypeCreated event) {
        final Conference.Builder conference = getState().toBuilder();
        final SeatType seatType = event.getSeatType();
        final SeatTypeId id = seatType.getId();
        if (seatTypesListContains(id)) {
            log().warn(format("Seat type with ID %s already exists. Conference ID: %s", id.getUuid(), conference.getId().getUuid()));
            return;
        }
        conference.addSeatType(seatType);
        sendAddSeatsRequest(id, seatType.getQuantityTotal());
        incrementState(conference.build());
    }

    private boolean seatTypesListContains(SeatTypeId id) {
        final List<SeatType> filtered = filterById(id, getState().getSeatTypeList());
        final boolean contains = !filtered.isEmpty();
        return contains;
    }

    @Subscribe
    public void on(SeatTypeUpdated event) {
        final Conference.Builder conference = getState().toBuilder();
        final SeatType newSeatType = event.getSeatType();
        final SeatTypeId id = newSeatType.getId();
        final List<SeatType> seatTypes = conference.getSeatTypeList();

        final List<SeatType> filtered = filterById(id, seatTypes);
        if (filtered.isEmpty()) {
            log().warn("No seat type with ID %s; conference ID: %s", id.getUuid(), conference.getId().getUuid());
            return;
        }
        final SeatType oldSeatType = filtered.get(0);
        final int oldTypeIndex = seatTypes.indexOf(oldSeatType);
        conference.setSeatType(oldTypeIndex, newSeatType);

        addOrRemoveSeats(newSeatType.getQuantityTotal(), oldSeatType.getQuantityTotal(), id);

        incrementState(conference.build());
    }

    private void addOrRemoveSeats(int newQuantity, int oldQuantity, SeatTypeId id) {
        final int difference = newQuantity - oldQuantity;
        if (difference > 0) {
            sendAddSeatsRequest(id, difference);
        } else if(difference < 0) {
            sendRemoveSeatsRequest(id, abs(difference));
        }
    }

    private void sendAddSeatsRequest(SeatTypeId seatTypeId, int quantity) {
        final AddSeats command = AddSeats.newBuilder()
                .setConferenceId(getState().getId())
                .setQuantity(newSeatQuantity(seatTypeId, quantity))
                .build();
        final CommandRequest request = newCommandRequest(command, CommandContext.getDefaultInstance());
        boundedContext.process(request);
    }

    private void sendRemoveSeatsRequest(SeatTypeId seatTypeId, int quantity) {
        final RemoveSeats command = RemoveSeats.newBuilder()
                .setConferenceId(getState().getId())
                .setQuantity(newSeatQuantity(seatTypeId, quantity))
                .build();
        final CommandRequest request = newCommandRequest(command, CommandContext.getDefaultInstance());
        boundedContext.process(request);
    }

    private static List<SeatType> filterById(final SeatTypeId id, List<SeatType> seatTypes) {
        final Iterable<SeatType> result = filter(seatTypes, new Predicate<SeatType>() {
            @Override
            public boolean apply(@Nullable SeatType input) {
                return (input != null) && input.getId().equals(id);
            }
        });
        return ImmutableList.copyOf(result);
    }

    private static Logger log() {
        return LogSingleton.INSTANCE.value;
    }

    private enum LogSingleton {
        INSTANCE;
        @SuppressWarnings("NonSerializableFieldInSerializableClass")
        private final Logger value = LoggerFactory.getLogger(ConferenceProjection.class);
    }
}
