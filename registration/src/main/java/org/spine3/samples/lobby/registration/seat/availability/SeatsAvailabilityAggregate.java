/*
 * Copyright 2018, TeamDev. All rights reserved.
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

package org.spine3.samples.lobby.registration.seat.availability;

import org.spine3.base.CommandContext;
import org.spine3.base.Event;
import org.spine3.samples.lobby.common.ImportEvents;
import org.spine3.samples.lobby.common.ReservationId;
import org.spine3.samples.lobby.common.SeatTypeId;
import org.spine3.samples.lobby.common.util.aggregate.AbstractLobbyAggregate;
import org.spine3.samples.lobby.registration.contracts.SeatQuantity;
import org.spine3.samples.lobby.registration.util.Seats;
import org.spine3.server.aggregate.Apply;
import org.spine3.server.command.Assign;

import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newLinkedList;
import static org.spine3.samples.lobby.registration.seat.availability.SeatsAvailabilityValidator.validateCommand;
import static org.spine3.samples.lobby.registration.seat.availability.SeatsAvailabilityValidator.validateState;
import static org.spine3.samples.lobby.registration.util.Seats.newSeatQuantities;
import static org.spine3.samples.lobby.registration.util.Seats.newSeatQuantity;

/**
 * The aggregate which manages the availability of conference seats.
 *
 * @author Alexander Litus
 */
@SuppressWarnings({"TypeMayBeWeakened"/** "OrBuilder" parameters are not applicable*/, "OverlyCoupledClass"})
public class SeatsAvailabilityAggregate extends AbstractLobbyAggregate<SeatsAvailabilityId, SeatsAvailability, SeatsAvailability.Builder> {

    /**
     * Creates a new instance.
     *
     * @param id the ID for the new instance
     * @throws IllegalArgumentException if the ID type is not supported
     */
    public SeatsAvailabilityAggregate(SeatsAvailabilityId id) {
        super(id);
    }

    @Assign
    public SeatsReserved handle(MakeSeatReservation cmd, CommandContext context) {
        validateCommand(cmd);

        final MakeSeatReservationCommandHandler handler = new MakeSeatReservationCommandHandler(getState());
        handler.handle(cmd);

        final SeatsReserved.Builder reserved = SeatsReserved.newBuilder()
                                                            .setReservationId(cmd.getReservationId())
                                                            .setConferenceId(cmd.getConferenceId())
                                                            .addAllReservedSeatUpdated(handler.getReservedSeatsUpdated())
                                                            .addAllAvailableSeatUpdated(handler.getAvailableSeatsUpdated());
        return reserved.build();
    }

    @Assign
    public SeatsReservationCommitted handle(CommitSeatReservation cmd, CommandContext context) {
        validateCommand(cmd);
        validateState(getState(), cmd);

        final SeatsReservationCommitted.Builder event = SeatsReservationCommitted.newBuilder()
                                                                                 .setReservationId(cmd.getReservationId());
        return event.build();
    }

    @Assign
    public SeatsReservationCancelled handle(CancelSeatReservation cmd, CommandContext context) {
        validateCommand(cmd);
        final SeatsAvailability state = getState();
        validateState(state, cmd);

        final ReservationId reservationId = cmd.getReservationId();
        final List<SeatQuantity> availableSeatsUpdated = newLinkedList(state.getAvailableSeatList());
        final SeatQuantities unreservedSeats = state.getPendingReservations()
                                                    .get(reservationId.getUuid());
        availableSeatsUpdated.addAll(unreservedSeats.getItemList());

        final SeatsReservationCancelled.Builder event = SeatsReservationCancelled.newBuilder()
                                                                                 .setReservationId(reservationId)
                                                                                 .setConferenceId(cmd.getConferenceId())
                                                                                 .addAllAvailableSeatUpdated(availableSeatsUpdated);
        return event.build();
    }

    @Assign
    public AddedAvailableSeats handle(AddSeats cmd, CommandContext context) {
        validateCommand(cmd);

        final AddedAvailableSeats.Builder event = AddedAvailableSeats.newBuilder()
                                                                     .setQuantity(cmd.getQuantity());
        return event.build();
    }

    @Assign
    public RemovedAvailableSeats handle(RemoveSeats cmd, CommandContext context) {
        validateCommand(cmd);
        validateState(getState(), cmd);

        final RemovedAvailableSeats.Builder event = RemovedAvailableSeats.newBuilder()
                                                                         .setQuantity(cmd.getQuantity());
        return event.build();
    }

    @Assign
    @Override
    public List<Event> handle(ImportEvents command, CommandContext ctx) {
        // Used to handle ImportEvents command for testing.
        return super.handle(command, ctx);
    }

    /* Event Appliers */

    @Apply
    private void apply(SeatsReserved event) {
        final SeatsAvailability.Builder state = getBuilder();
        state.clearAvailableSeat();
        state.addAllAvailableSeat(event.getAvailableSeatUpdatedList());
        final Map<String, SeatQuantities> pendingReservations = state.getMutablePendingReservations();
        final String reservationId = event.getReservationId()
                                          .getUuid();
        pendingReservations.put(reservationId, newSeatQuantities(event.getReservedSeatUpdatedList()));
    }

    @Apply
    private void apply(SeatsReservationCommitted event) {
        final SeatsAvailability.Builder state = getBuilder();
        final Map<String, SeatQuantities> pendingReservations = state.getMutablePendingReservations();
        final String reservationId = event.getReservationId()
                                          .getUuid();
        pendingReservations.remove(reservationId);
    }

    @Apply
    private void apply(SeatsReservationCancelled event) {
        final SeatsAvailability.Builder state = getBuilder();
        final Map<String, SeatQuantities> pendingReservations = state.getMutablePendingReservations();
        final String reservationId = event.getReservationId()
                                          .getUuid();
        pendingReservations.remove(reservationId);
        state.clearAvailableSeat();
        state.addAllAvailableSeat(event.getAvailableSeatUpdatedList());
    }

    @Apply
    private void apply(AddedAvailableSeats event) {
        final SeatsAvailability.Builder state = getBuilder();
        final List<SeatQuantity> availableSeats = state.getAvailableSeatList();
        final SeatQuantity addedQuantity = event.getQuantity();
        final SeatTypeId seatTypeId = addedQuantity.getSeatTypeId();
        final SeatQuantity existingOne = Seats.findById(availableSeats, seatTypeId, null);
        if (existingOne != null) {
            final int indexOfOldValue = availableSeats.indexOf(existingOne);
            final int newQuantity = existingOne.getQuantity() + addedQuantity.getQuantity();
            state.setAvailableSeat(indexOfOldValue, newSeatQuantity(seatTypeId, newQuantity));
        } else {
            state.addAvailableSeat(addedQuantity);
        }
    }

    @Apply
    private void apply(RemovedAvailableSeats event) {
        final SeatsAvailability.Builder state = getBuilder();
        final SeatQuantity removedQuantity = event.getQuantity();
        final SeatTypeId seatTypeId = removedQuantity.getSeatTypeId();
        final List<SeatQuantity> availableSeats = state.getAvailableSeatList();
        final SeatQuantity existingOne = Seats.findById(availableSeats, seatTypeId);
        final int indexOfOldValue = availableSeats.indexOf(existingOne);
        final int newQuantity = calculateNewQuantity(removedQuantity, existingOne);
        state.setAvailableSeat(indexOfOldValue, newSeatQuantity(seatTypeId, newQuantity));
    }

    private static int calculateNewQuantity(SeatQuantity removedQuantity, SeatQuantity existingOne) {
        final int newQuantity = existingOne.getQuantity() - removedQuantity.getQuantity();
        if (newQuantity < 0) {
            return 0;
        }
        return newQuantity;
    }
}
