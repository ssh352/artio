/*
 * Copyright 2015-2016 Real Logic Ltd.
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
package uk.co.real_logic.fix_gateway.library;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicCounter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.co.real_logic.fix_gateway.FixCounters;
import uk.co.real_logic.fix_gateway.messages.ControlNotificationDecoder.SessionsDecoder;
import uk.co.real_logic.fix_gateway.protocol.GatewayPublication;
import uk.co.real_logic.fix_gateway.replication.ClusterableSubscription;
import uk.co.real_logic.fix_gateway.session.Session;
import uk.co.real_logic.fix_gateway.timing.LibraryTimers;

import static io.aeron.CommonContext.IPC_CHANNEL;
import static java.util.Collections.singletonList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.*;
import static uk.co.real_logic.fix_gateway.messages.ConnectionType.ACCEPTOR;
import static uk.co.real_logic.fix_gateway.messages.SessionState.ACTIVE;

public class LibraryPollerTest
{
    private static final long CONNECTION_ID = 2;
    private static final long SESSION_ID = 3;
    private static final long OTHER_CONNECTION_ID = 4;
    private static final long OTHER_SESSION_ID = 5;

    private static final int LAST_SENT_SEQUENCE_NUMBER = 1;
    private static final int LAST_RECEIVED_SEQUENCE_NUMBER = 1;
    private static final int HEARTBEAT_INTERVAL_IN_S = 1;
    private static final int REPLY_TO_ID = 0;

    private ArgumentCaptor<Session> session = ArgumentCaptor.forClass(Session.class);
    private SessionHandler sessionHandler = mock(SessionHandler.class);
    private SessionAcquireHandler sessionAcquireHandler = mock(SessionAcquireHandler.class);
    private GatewayPublication outboundPublication = mock(GatewayPublication.class);
    private ClusterableSubscription inboundSubscription = mock(ClusterableSubscription.class);
    private LibraryTransport transport = mock(LibraryTransport.class);
    private FixCounters counters = mock(FixCounters.class);
    private FixLibrary library = mock(FixLibrary.class);
    private UnsafeBuffer address = new UnsafeBuffer(new byte[1024]);
    private int addressLength = address.putStringUtf8(0, "localhost:1234");
    private int libraryId;

    private LibraryPoller libraryPoller;

    @Before
    public void setUp()
    {
        when(transport.outboundPublication()).thenReturn(outboundPublication);
        when(transport.inboundSubscription()).thenReturn(inboundSubscription);

        when(counters.receivedMsgSeqNo(anyLong())).thenReturn(mock(AtomicCounter.class));
        when(counters.sentMsgSeqNo(anyLong())).thenReturn(mock(AtomicCounter.class));

        when(sessionAcquireHandler.onSessionAcquired(session.capture())).thenReturn(sessionHandler);

        when(inboundSubscription.controlledPoll(any(), anyInt())).then(inv ->
        {
            libraryPoller.onApplicationHeartbeat(libraryPoller.libraryId());
            return 1;
        });

        libraryPoller = new LibraryPoller(
            new LibraryConfiguration()
                .libraryAeronChannels(singletonList(IPC_CHANNEL))
                .sessionAcquireHandler(sessionAcquireHandler),
            new LibraryTimers(),
            counters,
            transport,
            library);

        libraryPoller.connect();

        libraryId = libraryPoller.libraryId();
    }

    @Test
    public void shouldNotifyClientOfSessionTimeouts()
    {
        manageConnection(CONNECTION_ID, SESSION_ID);

        libraryPoller.onControlNotification(libraryId, noSessionIds());

        verify(sessionHandler).onTimeout(libraryId, SESSION_ID);
    }

    @Test
    public void shouldNotifyClientsOfRelevantSessionTimeouts()
    {
        manageConnection(CONNECTION_ID, SESSION_ID);
        manageConnection(OTHER_CONNECTION_ID, OTHER_SESSION_ID);

        libraryPoller.onControlNotification(libraryId, hasOtherSessionId());

        verify(sessionHandler).onTimeout(libraryId, SESSION_ID);
    }

    private void manageConnection(final long connectionId, final long sessionId)
    {
        libraryPoller.onManageConnection(
            libraryId,
            connectionId,
            sessionId,
            ACCEPTOR,
            LAST_SENT_SEQUENCE_NUMBER,
            LAST_RECEIVED_SEQUENCE_NUMBER,
            address,
            0,
            addressLength,
            ACTIVE,
            HEARTBEAT_INTERVAL_IN_S,
            REPLY_TO_ID);
    }

    private SessionsDecoder hasOtherSessionId()
    {
        final SessionsDecoder sessionsDecoder = mock(SessionsDecoder.class);
        when(sessionsDecoder.hasNext()).thenReturn(true, false);
        when(sessionsDecoder.sessionId()).thenReturn(OTHER_SESSION_ID);
        return sessionsDecoder;
    }

    private SessionsDecoder noSessionIds()
    {
        final SessionsDecoder sessionsDecoder = mock(SessionsDecoder.class);
        when(sessionsDecoder.hasNext()).thenReturn(false);
        return sessionsDecoder;
    }

}
