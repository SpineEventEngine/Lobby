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

package org.spine3.samples.lobby.registration.seat.availability.testcase;

import org.spine3.samples.lobby.registration.contracts.SeatQuantity;
import org.spine3.samples.lobby.registration.seat.availability.AddedAvailableSeats;

import static org.junit.Assert.assertFalse;
import static org.spine3.samples.lobby.registration.util.Seats.newSeatQuantity;

/**
 * @author Alexander Litus
 */
@SuppressWarnings("MagicNumber")
public abstract class AddedAvailableSeatsEventApplying extends ExistAvailableSeatsAndPendingReservations {

    private static final SeatQuantity PRIMARY_SEAT = getAvailableSeats().get(0);

    public SeatQuantity getPrimarySeat() {
        return PRIMARY_SEAT;
    }

    public AddedAvailableSeats givenEvent() {
        final AddedAvailableSeats event = AddedAvailableSeats.newBuilder()
                                                             .setQuantity(getQuantityToAdd())
                                                             .build();
        return event;
    }

    protected abstract SeatQuantity getQuantityToAdd();

    public static class AddingNewSeatType extends AddedAvailableSeatsEventApplying {

        private final SeatQuantity quantityToAdd = newSeatQuantity(12);

        public AddingNewSeatType() {
            assertFalse(getAvailableSeats().contains(quantityToAdd));
        }

        @Override
        protected SeatQuantity getQuantityToAdd() {
            return quantityToAdd;
        }
    }

    public static class UpdatingSeatType extends AddedAvailableSeatsEventApplying {

        private final SeatQuantity quantityToAdd = newSeatQuantity(getPrimarySeat().getSeatTypeId(), 10);

        @Override
        protected SeatQuantity getQuantityToAdd() {
            return quantityToAdd;
        }
    }
}
