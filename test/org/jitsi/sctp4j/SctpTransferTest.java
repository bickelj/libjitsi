/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.sctp4j;

import org.junit.*;

import java.io.*;
import java.util.*;

import static org.junit.Assert.assertArrayEquals;

/**
 * Transfer tests.
 *
 * @author Pawel Domas
 */
public class SctpTransferTest
{
    private SctpSocket peerA;

    private final int portA = 5000;

    private SctpSocket peerB;

    private final int portB = 5001;

    private final Object transferLock = new Object();

    /** @GuardedBy("transferLock") */
    private byte[] receivedData = null;

    /** @GuardedBy("transferLock") */
    private boolean dataReady = false;

    /** Set random generator seed for consistent tests. */
    private static final Random rand = new Random(12345);

    @Before
    public void setUp()
    {
        Sctp.init();

        peerA = Sctp.createSocket(portA);
        peerB = Sctp.createSocket(portB);
    }

    @After
    public void tearDown()
        throws IOException
    {
        peerA.close();
        peerB.close();

        Sctp.finish();
    }

    public static byte[] createRandomData(int size)
    {
        byte[] dummy = new byte[size];
        getRandom().nextBytes(dummy);
        return dummy;
    }

    /**
     * Tests the transfer with random link failures and packet loss.
     *
     * @throws Exception
     */
    @Test
    public void testSocketBrokenLink()
        throws Exception
    {
        TestLink link = new TestLink(peerA, peerB,
                                     0.2, /* loss rate */
                                     0.1  /* error rate */);

        peerA.setLink(link);
        peerB.setLink(link);

        peerA.connect(portB);
        peerB.connect(portA);

        byte[] toSendA = createRandomData(2*1024);
        for(int i=0; i < 10; i++)
        {
            try
            {
                testTransferPart(peerA, peerB, toSendA);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private void testTransferPart(SctpSocket sender, SctpSocket receiver,
                                  byte[] testData)
        throws Exception
    {
        receiver.setDataCallback(new SctpDataCallback()
        {
            @Override
            public void onSctpPacket(byte[] data, int sid, int ssn, int tsn,
                                     long ppid,
                                     int context, int flags)
            {
                synchronized (transferLock)
                {
                    receivedData = data;
                    dataReady = true;
                    transferLock.notifyAll();
                }
            }
        });

        sender.send(testData, true, 0, 0);

        synchronized (transferLock)
        {
            while (!dataReady)
                transferLock.wait();
            assertArrayEquals(testData, receivedData);
	    dataReady = false;
        }
    }

    private static Random getRandom() {
        return rand;
    }
}
