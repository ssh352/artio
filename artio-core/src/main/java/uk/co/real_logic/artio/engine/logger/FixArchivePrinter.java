/*
 * Copyright 2015-2017 Real Logic Ltd.
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
package uk.co.real_logic.artio.engine.logger;

import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import uk.co.real_logic.artio.decoder.HeaderDecoder;
import uk.co.real_logic.artio.engine.logger.FixArchiveScanner.MessageType;
import uk.co.real_logic.artio.messages.FixMessageDecoder;

import java.util.function.Predicate;

import static java.lang.Long.parseLong;
import static uk.co.real_logic.artio.engine.logger.FixArchiveScanner.MessageType.SENT;
import static uk.co.real_logic.artio.engine.logger.FixMessagePredicates.*;

/**
 * Eg:
 * java uk.co.real_logic.artio.engine.logger.FixArchivePrinter \
 *   --log-file-dir=artio-system-tests/acceptor-logs/ \
 *   --aeron-channel=aeron:ipc
 */
public final class FixArchivePrinter
{
    public static void main(final String[] args)
    {
        String logFileDir = null;
        String aeronChannel = null;
        MessageType direction = SENT;
        FixMessagePredicate predicate = FixMessagePredicates.alwaysTrue();

        Predicate<HeaderDecoder> headerPredicate = null;

        for (final String arg : args)
        {
            final int eqIndex = arg.indexOf('=');
            final String optionName = eqIndex != -1 ? arg.substring(2, eqIndex) : arg;

            // Options without arguments
            switch (optionName)
            {
                case "help":
                    printHelp();
                    return;
            }

            // Options with arguments
            if (eqIndex == -1)
            {
                System.err.println("--help is the only option that doesn't take a value");
                printHelp();
                System.exit(-1);
            }

            final String optionValue = arg.substring(eqIndex + 1);

            switch (optionName)
            {
                case "from":
                    predicate = from(parseLong(optionValue)).and(predicate);
                    break;

                case "to":
                    predicate = to(parseLong(optionValue)).and(predicate);
                    break;

                case "message-types":
                    final String[] messageTypes = optionValue.split(",");
                    predicate = messageTypeOf(messageTypes).and(predicate);
                    break;

                case "sender-comp-id":
                    headerPredicate = safeAnd(headerPredicate, senderCompIdOf(optionValue));
                    break;

                case "target-comp-id":
                    headerPredicate = safeAnd(headerPredicate, targetCompIdOf(optionValue));
                    break;

                case "sender-sub-id":
                    headerPredicate = safeAnd(headerPredicate, senderSubIdOf(optionValue));
                    break;

                case "target-sub-id":
                    headerPredicate = safeAnd(headerPredicate, targetSubIdOf(optionValue));
                    break;

                case "sender-location-id":
                    headerPredicate = safeAnd(headerPredicate, senderLocationIdOf(optionValue));
                    break;

                case "target-location-id":
                    headerPredicate = safeAnd(headerPredicate, targetLocationIdOf(optionValue));
                    break;

                case "direction":
                    direction = MessageType.valueOf(optionValue.toUpperCase());
                    break;

                case "log-file-dir":
                    logFileDir = optionValue;
                    break;

                case "aeron-channel":
                    aeronChannel = optionValue;
                    break;
            }
        }

        requiredArgument(logFileDir, "log-file-dir");
        requiredArgument(aeronChannel, "aeron-channel");

        if (headerPredicate != null)
        {
            predicate = whereHeader(headerPredicate).and(predicate);
        }

        final FixArchiveScanner scanner = new FixArchiveScanner(logFileDir);
        scanner.scan(
            aeronChannel,
            direction,
            filterBy(FixArchivePrinter::print, predicate),
            Throwable::printStackTrace);
    }

    private static void requiredArgument(final String argument, final String description)
    {
        if (argument == null)
        {
            System.err.printf("Missing required --%s argument%n", description);
            printHelp();
            System.exit(-1);
        }
    }

    private static void printHelp()
    {
        System.out.println("FixArchivePrinter Options");
        System.out.println("All options are specified in the form: --optionName=optionValue");

        printOption(
            "log-file-dir",
            "Specifies the directory to look in, should be the same as your configuration.logFileDir()",
            true);
        printOption(
            "aeron-channel",
            "Specifies the aeron channel that was used to by the engine",
            true);

        printOption(
            "from",
            "Time in precision of CommonConfiguration.clock() that messages are not earlier than",
            false);
        printOption(
            "to",
            "Time in precision of CommonConfiguration.clock() that messages are not later than",
            false);
        printOption(
            "message-types",
            "Comma separated list of the message types (35=) that are printed",
            false);
        printOption(
            "sender-comp-id",
            "Only print messages where the header's sender comp id field matches this",
            false);
        printOption(
            "target-comp-id",
            "Only print messages where the header's sender comp id field matches this",
            false);
        printOption(
            "sender-sub-id",
            "Only print messages where the header's sender comp id field matches this",
            false);
        printOption(
            "target-sub-id",
            "Only print messages where the header's sender comp id field matches this",
            false);
        printOption(
            "sender-location-id",
            "Only print messages where the header's sender comp id field matches this",
            false);
        printOption(
            "target-location-id",
            "Only print messages where the header's sender comp id field matches this",
            false);
        printOption(
            "direction",
            "Only print messages where the direction matches this. Must be either 'sent' or 'received'." +
            "Defaults to sent.",
            false);
        printOption(
            "help",
            "Only prints this help message.",
            false);
    }

    private static void printOption(final String name, final String description, final boolean required)
    {
        System.out.printf("  --%-20s [%s] - %s%n", name, required ? "required" : "optional", description);
    }

    private static <T> Predicate<T> safeAnd(final Predicate<T> left, final Predicate<T> right)
    {
        return left == null ? right : left.and(right);
    }

    private static void print(
        final FixMessageDecoder message,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        System.out.println(message.body());
    }
}
