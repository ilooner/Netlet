/*
 * Copyright (c) 2013 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.netlet.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Thread.sleep;

/**
 * Provides a premium implementation of circular buffer<p>
 * <br>
 *
 * @param <T> type of the objects in this buffer.
 * @since 1.0.0
 */
public class CircularBuffer<T> implements UnsafeBlockingQueue<T>
{
  private final T[] buffer;
  private final int buffermask;
  private final int spinMillis;
  protected volatile long tail;
  protected volatile long head;

  private Lock lock = new ReentrantLock();
  private Condition condition = lock.newCondition();

  /**
   *
   * Constructing a circular buffer of 'n' integers<p>
   * <br>
   *
   * @param n size of the buffer to be constructed
   * @param spin time in milliseconds for which to wait before checking for expected value if it's missing
   * <br>
   */
  @SuppressWarnings("unchecked")
  public CircularBuffer(int n, int spin)
  {
    int i = 1;
    while (i < n) {
      i <<= 1;
    }

    buffer = (T[])new Object[i];
    buffermask = i - 1;

    spinMillis = spin;
  }

  private CircularBuffer(T[] buffer, int buffermask, int spinMillis)
  {
    this.buffer = buffer;
    this.buffermask = buffermask;
    this.spinMillis = spinMillis;
  }

  /**
   *
   * Constructing a circular buffer of 'n' integers<p>
   * <br>
   *
   * @param n size of the buffer to be constructed
   * <br>
   */
  public CircularBuffer(int n)
  {
    this(n, 10);
  }

  @Override
  public boolean add(T e)
  {
    boolean success = false;

    lock.lock();

    if (head - tail <= buffermask) {
      buffer[(int)(head & buffermask)] = e;

      head++;
      success = true;
    }

    condition.signal();
    lock.unlock();

    if(!success) {
      throw new IllegalStateException("Collection is full");
    }

    return success;
  }

  @Override
  public T remove()
  {
    T val = null;
    lock.lock();

    if (head > tail) {
      int pos = (int)(tail & buffermask);
      T t = buffer[pos];
      buffer[pos] = null;
      tail++;
      val = t;
    }

    condition.signal();
    lock.unlock();

    if(val == null) {
      throw new IllegalStateException("Collection is empty");
    }

    return val;
  }

  @Override
  public T peek()
  {
    T val = null;

    lock.lock();

    if (head > tail) {
      val = buffer[(int)(tail & buffermask)];
    }

    lock.unlock();

    return val;
  }

  @Override
  public int size()
  {
    return (int)(head - tail);
  }

  /**
   *
   * Total design capacity of the buffer<p>
   * <br>
   *
   * @return Total return capacity of the buffer
   * <br>
   */
  public int capacity()
  {
    return buffermask + 1;
  }

  @Override
  public int drainTo(Collection<? super T> container)
  {
    int size = size();

    lock.lock();

    while (head > tail) {
      container.add(buffer[(int)(tail & buffermask)]);
      tail++;
    }

    condition.signal();
    lock.unlock();

    return size;
  }

  @Override
  public String toString()
  {
    return "head=" + head + ", tail=" + tail + ", capacity=" + (buffermask + 1);
  }

  @Override
  public boolean offer(T e)
  {
    boolean success = false;

    lock.lock();

    if (head - tail <= buffermask) {
      buffer[(int)(head & buffermask)] = e;
      head++;
      success = true;
    }

    condition.signal();
    lock.unlock();

    return success;
  }

  @Override
  @SuppressWarnings("SleepWhileInLoop")
  public void put(T e) throws InterruptedException
  {
    do {
      lock.lock();

      if (head - tail < buffermask) {
        buffer[(int)(head & buffermask)] = e;
        head++;

        condition.signal();
        lock.unlock();
        return;
      }

      condition.await();
    }
    while (true);
  }

  @Override
  @SuppressWarnings("SleepWhileInLoop")
  public boolean offer(T e, long timeout, TimeUnit unit) throws InterruptedException
  {
    long millis = unit.toMillis(timeout);
    long start = System.currentTimeMillis();

    do {
      lock.lock();

      if (head - tail < buffermask) {
        buffer[(int)(head & buffermask)] = e;
        head++;
        condition.signal();
        lock.unlock();
        return true;
      }

      condition.await();
    }
    while (millis > (System.currentTimeMillis() - start));

    return false;
  }

  @Override
  @SuppressWarnings("SleepWhileInLoop")
  public T take() throws InterruptedException
  {
    do {
      lock.lock();

      if (head > tail) {
        int pos = (int)(tail & buffermask);
        T t = buffer[pos];
        buffer[pos] = null;
        tail++;
        condition.signal();
        lock.unlock();
        return t;
      }

      condition.await();
    }
    while (true);
  }

  @Override
  @SuppressWarnings("SleepWhileInLoop")
  public T poll(long timeout, TimeUnit unit) throws InterruptedException
  {
    long millis = unit.toMillis(timeout);
    long start = System.currentTimeMillis();

    do {
      lock.lock();

      if (head > tail) {
        int pos = (int)(tail & buffermask);
        T t = buffer[pos];
        buffer[pos] = null;
        tail++;
        condition.signal();
        lock.unlock();
        return t;
      }

      condition.await();
    }
    while (millis > (System.currentTimeMillis() - start));

    return null;
  }

  @Override
  public int remainingCapacity()
  {
    return buffermask + 1 - (int)(head - tail);
  }

  @Override
  public boolean remove(Object o)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean contains(Object o)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public int drainTo(final Collection<? super T> collection, final int maxElements)
  {
    int i = -1;
    int pos;

    lock.lock();

    while (i++ < maxElements && head > tail) {
      pos = (int)(tail & buffermask);
      collection.add(buffer[pos]);
      buffer[pos] = null;
      tail++;
    }

    condition.signal();
    lock.unlock();

    return i;
  }

  @Override
  public T poll()
  {
    T val = null;

    lock.lock();

    if (head > tail) {
      int pos = (int)(tail & buffermask);
      val = buffer[pos];
      buffer[pos] = null;
      tail++;
    }

    condition.signal();
    lock.unlock();

    return val;
  }

  @Override
  public T pollUnsafe()
  {
    lock.lock();

    int pos = (int)(tail & buffermask);
    T t = buffer[pos];
    buffer[pos] = null;
    tail++;

    condition.signal();
    lock.unlock();

    return t;
  }

  @Override
  public T element()
  {
    T val = null;

    if (head > tail) {
      val = buffer[(int)(tail & buffermask)];
    }

    if (val == null) {
      throw new IllegalStateException("Collection is empty");
    }

    return val;
  }

  @Override
  public boolean isEmpty()
  {
    boolean isEmpty = head == tail;
    return isEmpty;
  }

  private class FrozenIterator implements Iterator<T>, Iterable<T>, Cloneable
  {
    private final long frozenHead;
    private final long frozenTail;
    private long tail;

    FrozenIterator()
    {
      this(CircularBuffer.this.head, CircularBuffer.this.tail);
    }

    FrozenIterator(long frozenHead, long frozenTail)
    {
      this.frozenHead = frozenHead;
      this.frozenTail = frozenTail;
      this.tail = frozenTail;
    }

    @Override
    public boolean hasNext()
    {
      return tail < frozenHead;
    }

    @Override
    public T next()
    {
      return buffer[(int)(tail++ & buffermask)];
    }

    @Override
    public void remove()
    {
      buffer[(int)((tail - 1) & buffermask)] = null;
    }

    @Override
    public Iterator<T> iterator()
    {
      return new FrozenIterator(frozenHead, frozenTail);
    }

  }

  public Iterator<T> getFrozenIterator()
  {
    return new FrozenIterator();
  }

  public Iterable<T> getFrozenIterable()
  {
    return new FrozenIterator();
  }

  @Override
  public Iterator<T> iterator()
  {
    return new Iterator<T>()
    {
      @Override
      public boolean hasNext()
      {
        return head > tail;
      }

      @Override
      public T next()
      {
        lock.lock();
        int pos = (int)(tail & buffermask);
        T t = buffer[pos];
        buffer[pos] = null;
        tail++;
        condition.signal();
        lock.unlock();
        return t;
      }

      @Override
      public void remove()
      {
      }

    };
  }

  @Override
  public Object[] toArray()
  {
    lock.lock();
    final int count = (int)(head - tail);
    Object[] array = new Object[count];
    int pos;
    for (int i = 0; i < count; i++) {
      pos = (int)(tail & buffermask);
      array[i] = buffer[pos];
      buffer[pos] = null;
      tail++;
    }
    condition.signal();
    lock.unlock();
    return array;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T[] toArray(T[] a)
  {
    lock.lock();

    int count = (int)(head - tail);
    if (a.length < count) {
      a = (T[])new Object[count];
    }

    int pos;
    for (int i = 0; i < count; i++) {
      pos = (int)(tail & buffermask);
      a[i] = (T)buffer[pos];
      buffer[pos] = null;
      tail++;
    }

    condition.signal();
    lock.unlock();

    return a;
  }

  @Override
  public boolean containsAll(Collection<?> c)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean addAll(Collection<? extends T> c)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean removeAll(Collection<?> c)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean retainAll(Collection<?> c)
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void clear()
  {
    lock.lock();

    head = 0;
    tail = 0;
    Arrays.fill(buffer, null);

    condition.signal();
    lock.unlock();
  }

  @Override
  public T peekUnsafe()
  {
    return buffer[(int)(tail & buffermask)];
  }

  public CircularBuffer<T> getWhitehole(final String exceptionMessage)
  {
    CircularBuffer<T> cb = new CircularBuffer<T>(buffer, buffermask, spinMillis)
    {
      @Override
      public boolean add(T e)
      {
        throw new IllegalStateException(exceptionMessage);
      }

      @Override
      @SuppressWarnings("SleepWhileInLoop")
      public void put(T e) throws InterruptedException
      {
        while (true) {
          sleep(spinMillis);
        }
      }

      @Override
      public boolean offer(T e)
      {
        return false;
      }

      @Override
      public boolean offer(T e, long timeout, TimeUnit unit) throws InterruptedException
      {
        long millis = unit.toMillis(timeout);
        sleep(millis);
        return false;
      }

      @Override
      public int remainingCapacity()
      {
        return 0;
      }

      @Override
      public boolean addAll(Collection<? extends T> c)
      {
        throw new IllegalStateException(exceptionMessage);
      }

    };
    cb.head = head;
    cb.tail = tail;

    return cb;
  }

  private static final Logger logger = LoggerFactory.getLogger(CircularBuffer.class);
}
