/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.internal.operators.flowable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.MissingBackpressureException;
import io.reactivex.internal.disposables.DisposableHelper;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.internal.util.BackpressureHelper;

public final class FlowableIntervalRange extends Flowable<Long> {
    final Scheduler scheduler;
    final long start;
    final long end;
    final long initialDelay;
    final long period;
    final TimeUnit unit;

    public FlowableIntervalRange(long start, long end, long initialDelay, long period, TimeUnit unit, Scheduler scheduler) {
        this.initialDelay = initialDelay;
        this.period = period;
        this.unit = unit;
        this.scheduler = scheduler;
        this.start = start;
        this.end = end;
    }

    @Override
    public void subscribeActual(Subscriber<? super Long> s) {
        IntervalRangeSubscriber is = new IntervalRangeSubscriber(s, start, end);
        s.onSubscribe(is);

        Disposable d = scheduler.schedulePeriodicallyDirect(is, initialDelay, period, unit);

        is.setResource(d);
    }

    static final class IntervalRangeSubscriber extends AtomicLong
    implements Subscription, Runnable {

        private static final long serialVersionUID = -2809475196591179431L;

        final Subscriber<? super Long> actual;
        final long end;

        long count;

        final AtomicReference<Disposable> resource = new AtomicReference<Disposable>();

        IntervalRangeSubscriber(Subscriber<? super Long> actual, long start, long end) {
            this.actual = actual;
            this.count = start;
            this.end = end;
        }

        @Override
        public void request(long n) {
            if (SubscriptionHelper.validate(n)) {
                BackpressureHelper.add(this, n);
            }
        }

        @Override
        public void cancel() {
            DisposableHelper.dispose(resource);
        }

        @Override
        public void run() {
            if (resource.get() != DisposableHelper.DISPOSED) {
                long r = get();

                if (r != 0L) {
                    long c = count;
                    actual.onNext(c);

                    if (c == end) {
                        try {
                            actual.onComplete();
                        } finally {
                            DisposableHelper.dispose(resource);
                        }
                        return;
                    }

                    count = c + 1;

                    if (r != Long.MAX_VALUE) {
                        decrementAndGet();
                    }
                } else {
                    try {
                        actual.onError(new MissingBackpressureException("Can't deliver value " + count + " due to lack of requests"));
                    } finally {
                        DisposableHelper.dispose(resource);
                    }
                }
            }
        }

        public void setResource(Disposable d) {
            DisposableHelper.setOnce(resource, d);
        }
    }
}
