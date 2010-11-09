/**
 * (c) 2006 Cordys R&D B.V. All rights reserved. The computer program(s) is the
 * proprietary information of Cordys B.V. and provided under the relevant
 * License Agreement containing restrictions on use and disclosure. Use is
 * subject to the License Agreement.
 */
package com.cordys.coe.ac.jmsconnector.ext.webspheremq;

import com.cordys.coe.ac.jmsconnector.Destination;
import com.cordys.coe.ac.jmsconnector.JMSConnector;
import com.cordys.coe.ac.jmsconnector.ext.IProviderExtension;

import com.eibus.util.logger.Severity;

import com.ibm.mq.MQChannelDefinition;
import com.ibm.mq.MQChannelExit;
import com.ibm.mq.MQEnvironment;
import com.ibm.mq.MQSendExit;

import java.util.Arrays;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.jms.JMSException;
import javax.jms.Message;

/**
 * Sets the message ID of the outgoing message. This functionality is not allowed by the JMS
 * standard, so we use a work-around to implement it in the cases where it is absolutely required.
 *
 * <p>This extensions works as follows :</p>
 *
 * <ul>
 *   <li>Before a message is being sent a slot is allocated to that message (call will wait until a
 *     slot is available).</li>
 *   <li>Original message ID and correlation ID are copied from the message in to the slot object.
 *   </li>
 *   <li>The slot ID and an internal ID will be put in the correlation field.</li>
 *   <li>When the send exit is called, the slot ID will be read from the correlation ID field and
 *     the correct slot object will fecthed.</li>
 *   <li>The original message and correlation ID fields are put back in the message.</li>
 * </ul>
 *
 * @author  mpoyhone
 */
public class MQSetOutgoingMessageId
    implements IProviderExtension
{
    /**
     * If <code>true</code> the message contents are sent to the log.
     */
    private static final boolean bLogMessages = "true".equalsIgnoreCase(System.getProperty("jmsconnector.extensions.setmessageid.debug"));
    /**
     * If <code>true</code> the contents of unprocessed messages are sent to the log.
     */
    private static final boolean bLogUnprocessedMessages = "true".equalsIgnoreCase(System
                                                                                   .getProperty("jmsconnector.extensions.setmessageid.unprocessed.debug"));

    /**
     * An arbitrary magic number to be put in the correlation ID field. This is just an extra check.
     */
    private static final byte[] baMagicNumber = { 0x02, (byte) 0xFF, 0x03 };
    /**
     * Contains message information objects.
     */
    private static final MsgSlot[] msaSlotArray = new MsgSlot[50];
    /**
     * Contains free message slots.
     */
    private static final BlockingQueue<MsgSlot> bqFreeSlotQueue;

    /**
     * Contains the message ID offset in the MQMD header.
     */
    private static final int MQ_MESSAGEID_OFFSET = 92;

    /**
     * Contains the message ID length in the MQMD header.
     */
    private static final int MQ_MESSAGEID_LENGTH = 24;
    /**
     * Contains the correlation ID offset in the MQMD header.
     */
    private static final int MQ_CORRELATIONID_OFFSET = 116;
    /**
     * Contains the correlation ID length in the MQMD header.
     */
    private static final int MQ_CORRELATIONID_LENGTH = 24;
    /**
     * Start offset of the MQMD header structure.
     */
    private static final int MQ_MQMD_START_OFFSET = 44;
    /**
     * Start offset of the MQPMO header structure.
     */
    private static final int MQ_MQPMO_START_OFFSET = 408;

    /**
     * Slot ID's in the correlation ID field start from this value. Used to avoid having a zero (the
     * default value) in the field.
     */
    private static final int SLOTID_BASE = 0x10;

    static
    {
        // Initialize the slots.
        bqFreeSlotQueue = new ArrayBlockingQueue<MsgSlot>(msaSlotArray.length, true);

        for (int i = 0; i < msaSlotArray.length; i++)
        {
            MsgSlot msSlot = new MsgSlot();

            msSlot.bInUse = false;
            msSlot.iSlotId = i;
            msaSlotArray[i] = msSlot;

            try
            {
                bqFreeSlotQueue.put(msSlot);
            }
            catch (InterruptedException ignored)
            {
            }
        }
    }

    /**
     * Creates a new MQSetOutgoingMessageId object.
     */
    public MQSetOutgoingMessageId()
    {
    }

    /**
     * Creates a new MQSetOutgoingMessageId object.
     *
     * @param  sArg  DOCUMENTME
     */
    public MQSetOutgoingMessageId(String sArg)
    {
    }

    /**
     * @see  com.cordys.coe.ac.jmsconnector.ext.IProviderExtension#initialize(com.cordys.coe.ac.jmsconnector.JMSConnector)
     */
    public boolean initialize(JMSConnector jcConnector)
    {
        if (MQEnvironment.sendExit == this)
        {
            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                // Extension has already been enabled.
                JMSConnector.jmsLogger.debug("MQSetOutgoingMessageId: This handler is already installed.");
            }
            return false;
        }

        if (MQEnvironment.sendExit != null)
        {
            JMSConnector.jmsLogger.log(Severity.WARN,
                                       "MQSetOutgoingMessageId: MQ send exit handler is already set - overriding it.");
        }

        // Set the send exit.
        MQEnvironment.sendExit = new MQSendExit()
            {
                public byte[] sendExit(MQChannelExit arg0, MQChannelDefinition arg1, byte[] arg2)
                {
                    return sendExitHandler(arg0, arg1, arg2);
                }
            };

        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("MQSetOutgoingMessageId: Handler is installed successfully.");
        }

        return true;
    }

    /**
     * @see  com.cordys.coe.ac.jmsconnector.ext.IProviderExtension#onBeforeSendMessage(com.cordys.coe.ac.jmsconnector.Destination,
     *       javax.jms.Message)
     */
    public void onBeforeSendMessage(Destination dDest, Message mMsg)
    {
        prepareMessageSlot(mMsg);
    }

    /**
     * Prepares a message slot for this message. The slot ID will be put in the message correlation
     * ID field.
     *
     * @param   mMsg  Message.
     *
     * @throws  IllegalStateException  Thrown if an internal error occurred.
     */
    public void prepareMessageSlot(Message mMsg)
                            throws IllegalStateException
    {
        byte[] baMessageId = null;
        byte[] baCorrelationId;

        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Allocating a slot for an outgoing message.");
        }

        try
        {
            String sTmp = mMsg.getJMSMessageID();

            if (sTmp != null)
            {
                if (sTmp.startsWith("ID:") && (sTmp.length() > 3))
                {
                    // Ignore the JMS ID prefix.
                    sTmp = sTmp.substring(3);

                    // Convert the hex string to bytes.
                    if ((sTmp.length() % 2) != 0)
                    {
                        // Odd length, so add a zero to the beginning.
                        sTmp = "0" + sTmp;
                    }

                    int iLength = sTmp.length();

                    baMessageId = new byte[iLength / 2];

                    for (int i = 0; i < iLength; i += 2)
                    {
                        byte b = (byte) Integer.parseInt(sTmp.substring(i, i + 2), 16);

                        baMessageId[i / 2] = b;
                    }
                }
                else
                {
                    baMessageId = sTmp.getBytes();
                }
            }
        }
        catch (JMSException e)
        {
            throw new IllegalStateException("Unable to get the message ID.", e);
        }

        try
        {
            baCorrelationId = mMsg.getJMSCorrelationIDAsBytes();
        }
        catch (JMSException e)
        {
            throw new IllegalStateException("Unable to get the message correlation ID.", e);
        }

        MsgSlot msSlot = null;

        for (int i = 0; i < 100; i++)
        {
            try
            {
                msSlot = bqFreeSlotQueue.take();
                break;
            }
            catch (InterruptedException e)
            {
                // This should never happen.
                continue;
            }
        }

        if (msSlot == null)
        {
            throw new IllegalStateException("Could not allocate a message slot.");
        }

        if (msSlot.bInUse)
        {
            throw new IllegalStateException(String.format("Message slot ID %d is already in use.",
                                                          msSlot.iSlotId));
        }

        // Mark the slot used.
        msSlot.bInUse = true;

        int iSlotId = SLOTID_BASE + msSlot.iSlotId;

        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Using message slot ID: " + iSlotId);
        }

        // Copy message ID's to the slot.
        msSlot.copyMessageIdFrom(baMessageId);
        msSlot.copyCorrelationIdFrom(baCorrelationId);

        // Replace the correlation ID in the message.
        byte[] baNewCorrelationId = new byte[baMagicNumber.length + 1];

        System.arraycopy(baMagicNumber, 0, baNewCorrelationId, 0, baMagicNumber.length);
        baNewCorrelationId[baMagicNumber.length] = (byte) iSlotId;

        try
        {
            mMsg.setJMSCorrelationIDAsBytes(baNewCorrelationId);
        }
        catch (JMSException e)
        {
            // Return the slot back to the queue.
            msSlot.clearSlot();
            throw new IllegalStateException("Could not set the correlation ID in the message.", e);
        }
    }

    /**
     * @see  com.ibm.mq.MQSendExit#sendExit(com.ibm.mq.MQChannelExit,com.ibm.mq.MQChannelDefinition,
     *       byte[])
     */
    public byte[] sendExitHandler(MQChannelExit arg0, MQChannelDefinition arg1, byte[] baMsg)
    {
        // Check that the message length is correct.
        if (baMsg.length < (MQ_MQPMO_START_OFFSET + 4))
        {
            // Not a valid message for us.
            if (bLogUnprocessedMessages && JMSConnector.jmsLogger.isDebugEnabled())
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Skipping message:\n" +
                                                 writeMessageToString(baMsg));
                }
            }

            return baMsg;
        }

        // Check that this is a message we are sending.
        if ((baMsg[MQ_MQMD_START_OFFSET] != 'M') || (baMsg[MQ_MQMD_START_OFFSET + 1] != 'D') ||
                (baMsg[MQ_MQMD_START_OFFSET + 2] != ' ') ||
                (baMsg[MQ_MQMD_START_OFFSET + 3] != ' '))
        {
            // Not a valid message for us.
            if (bLogUnprocessedMessages && JMSConnector.jmsLogger.isDebugEnabled())
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Skipping message:\n" +
                                                 writeMessageToString(baMsg));
                }
            }
            return baMsg;
        }

        // Check that this is a message we are sending.
        if ((baMsg[MQ_MQPMO_START_OFFSET] != 'P') || (baMsg[MQ_MQPMO_START_OFFSET + 1] != 'M') ||
                (baMsg[MQ_MQPMO_START_OFFSET + 2] != 'O') ||
                (baMsg[MQ_MQPMO_START_OFFSET + 3] != ' '))
        {
            // Not a valid message for us.
            if (bLogUnprocessedMessages && JMSConnector.jmsLogger.isDebugEnabled())
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Skipping message:\n" +
                                                 writeMessageToString(baMsg));
                }
            }

            return baMsg;
        }

        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Preparing to set the message and correlation ID fields in the outgoing message.");
        }

        // Check that the magic number matches.
        for (int i = 0; i < baMagicNumber.length; i++)
        {
            if (baMsg[MQ_CORRELATIONID_OFFSET + i] != baMagicNumber[i])
            {
                JMSConnector.jmsLogger.log(Severity.FATAL,
                                           "Message slot header ID is not set for the outgoing message.");

                if (bLogMessages && JMSConnector.jmsLogger.isDebugEnabled())
                {
                    if (JMSConnector.jmsLogger.isDebugEnabled())
                    {
                        JMSConnector.jmsLogger.debug("Offending message:\n" +
                                                     writeMessageToString(baMsg));
                    }
                }

                return baMsg;
            }
        }

        // Try to fetch the correct slot.
        int iSlotId = baMsg[MQ_CORRELATIONID_OFFSET + baMagicNumber.length];

        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Got slot ID: " + iSlotId);
        }

        iSlotId -= SLOTID_BASE;

        if ((iSlotId < 0) || (iSlotId > msaSlotArray.length))
        {
            JMSConnector.jmsLogger.log(Severity.FATAL,
                                       "Invalid slot ID set for the outgoing message.");

            if (bLogMessages && JMSConnector.jmsLogger.isDebugEnabled())
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Offending message:\n" +
                                                 writeMessageToString(baMsg));
                }
            }

            return baMsg;
        }

        MsgSlot msSlot = msaSlotArray[iSlotId];

        if (!msSlot.bInUse)
        {
            JMSConnector.jmsLogger.log(Severity.FATAL, "Message slot is not in use.");

            if (bLogMessages && JMSConnector.jmsLogger.isDebugEnabled())
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Offending message:\n" +
                                                 writeMessageToString(baMsg));
                }
            }

            return baMsg;
        }

        // Copy the message and correlation ID from the slot.
        System.arraycopy(msSlot.baMessageId, 0, baMsg, MQ_MESSAGEID_OFFSET,
                         msSlot.baMessageId.length);
        System.arraycopy(msSlot.baCorrelationId, 0, baMsg, MQ_CORRELATIONID_OFFSET,
                         msSlot.baCorrelationId.length);

        // Free the slot.
        msSlot.clearSlot();

        for (int i = 0; i < 100; i++)
        {
            try
            {
                bqFreeSlotQueue.put(msSlot);
                break;
            }
            catch (InterruptedException e)
            {
                // This should never happen.
                continue;
            }
        }

        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Correlation and message ID's set succesfully.");
        }

        if (bLogMessages && JMSConnector.jmsLogger.isDebugEnabled())
        {
            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Sending message:\n" + writeMessageToString(baMsg));
            }
        }

        return baMsg;
    }

    /**
     * Writes the message contents into a debug string.
     *
     * @param   baMsg  Message data.
     *
     * @return  Debug string.
     */
    private String writeMessageToString(byte[] baMsg)
    {
        StringBuilder sb = new StringBuilder(200);

        for (int bp = 0, pos = 0; bp < baMsg.length; bp++)
        {
            byte b = baMsg[bp];
            String s = Integer.toHexString(b & 0xFF);

            for (int i = 0; s.length() < 2; i++)
            {
                s = "0" + s;
            }

            s += " ";

            if (pos == 0)
            {
                sb.append(String.format("%03d", bp)).append("| ");
            }

            sb.append(s);

            if (++pos >= 20)
            {
                sb.append("\n");
                pos = 0;
            }
        }

        sb.append("\n");

        for (int bp = 0, pos = 0; bp < baMsg.length; bp++)
        {
            byte b = baMsg[bp];

            if (pos == 0)
            {
                sb.append(String.format("%03d", bp)).append("| ");
            }

            sb.append((b >= 32) ? (char) b : '.');

            if (++pos == 40)
            {
                sb.append("\n");
                pos = 0;
            }
        }

        sb.append("\n");

        return sb.toString();
    }

    /**
     * Contains message ID that will be set in the message to be sent. The user exit finds the
     * correct slot object based on the message correlation ID value and will set these values back
     * to the message.
     *
     * @author  mpoyhone
     */
    static class MsgSlot
    {
        /**
         * Contains the correlation ID as bytes.
         */
        byte[] baCorrelationId = new byte[MQ_CORRELATIONID_LENGTH];
        /**
         * Contains the message ID as bytes.
         */
        byte[] baMessageId = new byte[MQ_MESSAGEID_LENGTH];
        /**
         * If <code>true</code> this slot is being used.
         */
        boolean bInUse;
        /**
         * Slot array position that this object belongs to.
         */
        int iSlotId;

        /**
         * Clears the slot information so it can be put in the queue.
         */
        void clearSlot()
        {
            bInUse = false;
            Arrays.fill(baMessageId, (byte) 0);
            Arrays.fill(baCorrelationId, (byte) 0);
        }

        /**
         * Copies the correlation ID from the given array.
         *
         * @param  baSrc  Correlation ID.
         */
        void copyCorrelationIdFrom(byte[] baSrc)
        {
            if (baSrc != null)
            {
                int iLength = Math.min(baSrc.length, baCorrelationId.length);

                System.arraycopy(baSrc, 0, baCorrelationId, 0, iLength);

                if (iLength < baCorrelationId.length)
                {
                    // Pad with spaces.
                    Arrays.fill(baCorrelationId, iLength, baCorrelationId.length, (byte) ' ');
                }
            }
            else
            {
                // Use the MQCI_NONE.
                Arrays.fill(baCorrelationId, (byte) 0);
            }
        }

        /**
         * Copies the message ID from the given array.
         *
         * @param  baSrc  Message ID.
         */
        void copyMessageIdFrom(byte[] baSrc)
        {
            if (baSrc != null)
            {
                int iLength = Math.min(baSrc.length, baMessageId.length);

                System.arraycopy(baSrc, 0, baMessageId, 0, iLength);

                if (iLength < baMessageId.length)
                {
                    // Pad with spaces.
                    Arrays.fill(baMessageId, iLength, baMessageId.length, (byte) ' ');
                }
            }
            else
            {
                // Use the MQMI_NONE.
                Arrays.fill(baMessageId, (byte) 0);
            }
        }
    }
}
