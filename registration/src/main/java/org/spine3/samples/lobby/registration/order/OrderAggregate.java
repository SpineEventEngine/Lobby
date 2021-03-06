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

package org.spine3.samples.lobby.registration.order;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Duration;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import org.spine3.base.CommandContext;
import org.spine3.base.Event;
import org.spine3.money.Money;
import org.spine3.protobuf.Durations;
import org.spine3.samples.lobby.common.ConferenceId;
import org.spine3.samples.lobby.common.ImportEvents;
import org.spine3.samples.lobby.common.OrderId;
import org.spine3.samples.lobby.common.util.RandomPasswordGenerator;
import org.spine3.samples.lobby.common.util.aggregate.AbstractLobbyAggregate;
import org.spine3.samples.lobby.registration.contracts.OrderAccessCode;
import org.spine3.samples.lobby.registration.contracts.OrderConfirmed;
import org.spine3.samples.lobby.registration.contracts.OrderExpired;
import org.spine3.samples.lobby.registration.contracts.OrderPartiallyReserved;
import org.spine3.samples.lobby.registration.contracts.OrderPlaced;
import org.spine3.samples.lobby.registration.contracts.OrderRegistrantAssigned;
import org.spine3.samples.lobby.registration.contracts.OrderReservationCompleted;
import org.spine3.samples.lobby.registration.contracts.OrderTotal;
import org.spine3.samples.lobby.registration.contracts.OrderTotalsCalculated;
import org.spine3.samples.lobby.registration.contracts.OrderUpdated;
import org.spine3.samples.lobby.registration.contracts.SeatQuantity;
import org.spine3.samples.lobby.registration.util.Seats;
import org.spine3.server.aggregate.Apply;
import org.spine3.server.command.Assign;
import org.spine3.server.entity.Entity;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

import static com.google.common.collect.Collections2.filter;
import static com.google.protobuf.util.TimeUtil.add;
import static com.google.protobuf.util.TimeUtil.getCurrentTime;
import static org.spine3.samples.lobby.registration.order.OrderValidator.*;

/**
 * The order aggregate which manages the state of the order.
 *
 * @author Alexander Litus
 */
@SuppressWarnings({"TypeMayBeWeakened", "OverlyCoupledClass"})
public class OrderAggregate extends AbstractLobbyAggregate<OrderId, Order, Order.Builder> {

    /**
     * The period in minutes after which the reservation expires.
     */
    private static final int EXPIRATION_PERIOD_MINUTES = 15;

    private static final Duration RESERVATION_EXPIRATION_PERIOD = Durations.ofMinutes(EXPIRATION_PERIOD_MINUTES);

    private static final int ACCESS_CODE_LENGTH = 8;

    private OrderPricingService pricingService;

    /**
     * Creates a new instance.
     *
     * @param id the ID for the new instance
     * @throws IllegalArgumentException if the ID type is not supported
     * @see Entity
     */
    public OrderAggregate(OrderId id) {
        super(id);
    }

    /**
     * Sets the pricing service to use to calculate a price of an order.
     *
     * @param service the pricing service implementation
     */
    public void setOrderPricingService(OrderPricingService service) {
        this.pricingService = service;
    }

    @Assign
    public List<Message> handle(RegisterToConference command, CommandContext context) {
        checkNotConfirmed(getState(), command);
        validateCommand(command);

        final ImmutableList.Builder<Message> result = ImmutableList.builder();
        final boolean isNew = getVersion() == 0;
        if (isNew) {
            final OrderPlaced placed = EventFactory.orderPlaced(command);
            result.add(placed);
        } else {
            final OrderUpdated updated = EventFactory.orderUpdated(command);
            result.add(updated);
        }
        final OrderTotalsCalculated totalsCalculated = buildOrderTotalsCalculated(command.getOrderId(),
                command.getConferenceId(), command.getSeatList());
        result.add(totalsCalculated);
        return result.build();
    }

    @Assign
    public List<Message> handle(MarkSeatsAsReserved command, CommandContext context) {
        checkNotConfirmed(getState(), command);
        validateCommand(command);

        final ImmutableList.Builder<Message> result = ImmutableList.builder();
        final List<SeatQuantity> reservedSeats = command.getSeatList();

        if (isOrderPartiallyReserved(reservedSeats)) {
            final OrderPartiallyReserved partiallyReserved = EventFactory.orderPartiallyReserved(command);
            result.add(partiallyReserved);

            final OrderTotalsCalculated newTotalsCalculated = buildOrderTotalsCalculated(command.getOrderId(),
                    getState().getConferenceId(), command.getSeatList());
            result.add(newTotalsCalculated);
        } else {
            final OrderReservationCompleted reservationCompleted = EventFactory.orderReservationCompleted(command);
            result.add(reservationCompleted);
        }
        return result.build();
    }

    @Assign
    public OrderExpired handle(RejectOrder command, CommandContext context) {
        checkNotConfirmed(getState(), command);
        validateCommand(command);
        final OrderExpired result = OrderExpired.newBuilder()
                                                .setOrderId(command.getOrderId())
                                                .build();
        return result;
    }

    @Assign
    public OrderConfirmed handle(ConfirmOrder command, CommandContext context) {
        final Order state = getState();
        checkNotConfirmed(state, command);
        validateCommand(command);
        final OrderConfirmed result = OrderConfirmed.newBuilder()
                                                    .setOrderId(command.getOrderId())
                                                    .addAllSeat(state.getSeatList())
                                                    .build();
        return result;
    }

    @Assign
    public OrderRegistrantAssigned handle(AssignRegistrantDetails command, CommandContext context) {
        validateCommand(command);
        final OrderRegistrantAssigned result = OrderRegistrantAssigned.newBuilder()
                                                                      .setOrderId(command.getOrderId())
                                                                      .setPersonalInfo(command.getRegistrant())
                                                                      .build();
        return result;
    }


    @Assign
    @Override
    public List<Event> handle(ImportEvents command, CommandContext ctx) {
        // Used to handle ImportEvents command for testing.
        return super.handle(command, ctx);
    }

    /* Event Appliers */

    @Apply
    private void apply(OrderPlaced event) {
        getBuilder()
                .setId(event.getOrderId())
                .setConferenceId(event.getConferenceId())
                .addAllSeat(event.getSeatList());
    }

    @Apply
    private void apply(OrderUpdated event) {
        updateSeats(event.getSeatList());
    }

    @Apply
    private void apply(OrderPartiallyReserved event) {
        updateSeats(event.getSeatList());
    }

    @Apply
    private void apply(OrderReservationCompleted event) {
        updateSeats(event.getSeatList());
    }

    @Apply
    private void apply(OrderTotalsCalculated event) {
        final Money price = event.getTotal();
        getBuilder().setPrice(price);
    }

    @Apply
    private void apply(OrderExpired event) {
        getBuilder().setIsExpired(true);
    }

    @Apply
    private void apply(OrderRegistrantAssigned event) {
        getBuilder().setRegistrant(event.getPersonalInfo());
    }

    @Apply
    private void apply(OrderConfirmed event) {
        getBuilder().setIsConfirmed(true);
    }

    private void updateSeats(List<SeatQuantity> newSeats) {
        getBuilder().clearSeat()
                    .addAllSeat(newSeats);
    }

    private boolean isOrderPartiallyReserved(final List<SeatQuantity> reservedSeats) {
        final List<SeatQuantity> requestedSeats = getState().getSeatList();
        if (reservedSeats.size() < requestedSeats.size()) {
            return true;
        }
        final Collection<SeatQuantity> partlyReservedSeats = findPartlyReservedSeats(requestedSeats, reservedSeats);
        final boolean isPartlyReserved = partlyReservedSeats.size() > 0;
        return isPartlyReserved;
    }

    private static Collection<SeatQuantity> findPartlyReservedSeats(final List<SeatQuantity> requestedSeats,
                                                                    final List<SeatQuantity> reservedSeats) {
        final Collection<SeatQuantity> result = filter(requestedSeats, new Predicate<SeatQuantity>() {
            @Override
            public boolean apply(@Nullable SeatQuantity requestedOne) {
                if (requestedOne == null) {
                    return false;
                }
                final SeatQuantity reservedOne = Seats.findById(reservedSeats, requestedOne.getSeatTypeId(), null);
                if (reservedOne == null) {
                    return false;
                }
                final boolean isPartlyReserved = reservedOne.getQuantity() < requestedOne.getQuantity();
                return isPartlyReserved;
            }
        });
        return result;
    }

    @Override
    @SuppressWarnings("RefusedBequest") // method from superclass does nothing
    protected void validate(Order newState) throws IllegalStateException {
        final int version = getVersion();
        if (version > 0) {
            checkNewState(newState);
        }
    }

    private OrderTotalsCalculated buildOrderTotalsCalculated(OrderId orderId, ConferenceId conferenceId, List<SeatQuantity> seats) {
        final OrderTotal total = pricingService.calculateTotalOrderPrice(conferenceId, seats);
        return EventFactory.orderTotalsCalculated(orderId, total);
    }

    private static class EventFactory {

        private static OrderTotalsCalculated orderTotalsCalculated(OrderId orderId, OrderTotal total) {
            final OrderTotalsCalculated.Builder result = OrderTotalsCalculated.newBuilder()
                                                                              .setOrderId(orderId)
                                                                              .addAllOrderLine(total.getOrderLineList());

            final Money totalPrice = total.getTotalPrice();
            if (totalPrice.getAmount() > 0) {
                result.setTotal(totalPrice);
            } else {
                result.setIsFreeOfCharge(true);
            }
            return result.build();
        }

        private static OrderPlaced orderPlaced(RegisterToConference command) {
            final Timestamp expirationTime = add(getCurrentTime(), RESERVATION_EXPIRATION_PERIOD);
            final String code = RandomPasswordGenerator.generate(ACCESS_CODE_LENGTH);
            final OrderAccessCode accessCode = OrderAccessCode.newBuilder()
                                                              .setValue(code)
                                                              .build();

            final OrderPlaced.Builder result = OrderPlaced.newBuilder()
                                                          .setOrderId(command.getOrderId())
                                                          .setConferenceId(command.getConferenceId())
                                                          .addAllSeat(command.getSeatList())
                                                          .setReservationAutoExpiration(expirationTime)
                                                          .setAccessCode(accessCode);
            return result.build();
        }

        private static OrderUpdated orderUpdated(RegisterToConference command) {
            final OrderUpdated.Builder result = OrderUpdated.newBuilder()
                                                            .setOrderId(command.getOrderId())
                                                            .addAllSeat(command.getSeatList());
            return result.build();
        }

        private static OrderPartiallyReserved orderPartiallyReserved(MarkSeatsAsReserved command) {
            final OrderPartiallyReserved.Builder result = OrderPartiallyReserved.newBuilder()
                                                                                .setOrderId(command.getOrderId())
                                                                                .setReservationExpiration(command.getReservationExpiration())
                                                                                .addAllSeat(command.getSeatList());
            return result.build();
        }

        private static OrderReservationCompleted orderReservationCompleted(MarkSeatsAsReserved command) {
            final OrderReservationCompleted.Builder result = OrderReservationCompleted.newBuilder()
                                                                                      .setOrderId(command.getOrderId())
                                                                                      .setReservationExpiration(command.getReservationExpiration())
                                                                                      .addAllSeat(command.getSeatList());
            return result.build();
        }
    }
}
