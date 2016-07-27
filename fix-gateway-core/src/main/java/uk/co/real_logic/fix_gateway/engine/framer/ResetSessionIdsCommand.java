/*
 * Copyright 2014-2016 Real Logic Ltd.
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
package uk.co.real_logic.fix_gateway.engine.framer;

import org.agrona.LangUtil;
import org.agrona.concurrent.IdleStrategy;

import java.io.File;

final class ResetSessionIdsCommand implements AdminCommand
{
    private final File backupLocation;

    private volatile Exception error;
    private volatile boolean done;

    ResetSessionIdsCommand(final File backupLocation)
    {
        this.backupLocation = backupLocation;
    }

    public void execute(final Framer framer)
    {
        framer.onResetSessionIds(backupLocation, this);
    }

    void awaitResponse(final IdleStrategy idleStrategy)
    {
        while (!isDone() && error == null)
        {
            idleStrategy.idle();
        }
        idleStrategy.reset();

        if (!isDone())
        {
            LangUtil.rethrowUnchecked(error);
        }
    }

    boolean isDone()
    {
        return done;
    }

    void onError(final Exception error)
    {
        this.error = error;
    }

    void success()
    {
        done = true;
    }
}
