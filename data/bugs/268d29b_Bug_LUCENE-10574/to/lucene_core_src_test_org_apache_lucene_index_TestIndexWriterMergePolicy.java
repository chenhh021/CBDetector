/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.MockIndexWriterEventListener;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.mockfile.HandleLimitFS;
import org.apache.lucene.tests.util.LuceneTestCase;

@HandleLimitFS.MaxOpenHandles(limit = HandleLimitFS.MaxOpenHandles.MAX_OPEN_FILES * 2)
// Some of these tests are too intense for SimpleText
@LuceneTestCase.SuppressCodecs("SimpleText")
public class TestIndexWriterMergePolicy extends LuceneTestCase {

  /**
   * A less sophisticated version of LogDocMergePolicy, only for testing the interaction between
   * IndexWriter and the MergePolicy.
   */
  private static class MockMergePolicy extends MergePolicy {

    private int mergeFactor = 10;

    public int getMergeFactor() {
      return mergeFactor;
    }

    public void setMergeFactor(int mergeFactor) {
      this.mergeFactor = mergeFactor;
    }

    @Override
    public MergeSpecification findMerges(
        MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext)
        throws IOException {
      List<SegmentCommitInfo> segments = new ArrayList<>();
      for (SegmentCommitInfo sci : segmentInfos) {
        segments.add(sci);
      }
      MergeSpecification spec = null;
      for (int start = 0; start < segments.size(); ) {
        final int end = start + mergeFactor;
        if (end > segments.size()) {
          break;
        }
        int minDocCount = Integer.MAX_VALUE;
        int maxDocCount = 0;
        for (int i = start; i < end; ++i) {
          int docCount = segments.get(i).info.maxDoc();
          minDocCount = Math.min(docCount, minDocCount);
          maxDocCount = Math.max(docCount, maxDocCount);
        }
        if (maxDocCount < (long) mergeFactor * minDocCount) {
          // Segment sizes differ by less than mergeFactor, they can be merged together
          if (spec == null) {
            spec = new MergeSpecification();
          }
          spec.add(new OneMerge(segments.subList(start, end)));
          start = end;
        } else {
          start++;
        }
      }
      return spec;
    }

    @Override
    public MergeSpecification findForcedMerges(
        SegmentInfos segmentInfos,
        int maxSegmentCount,
        Map<SegmentCommitInfo, Boolean> segmentsToMerge,
        MergeContext mergeContext)
        throws IOException {
      return null;
    }

    @Override
    public MergeSpecification findForcedDeletesMerges(
        SegmentInfos segmentInfos, MergeContext mergeContext) throws IOException {
      return null;
    }
  }

  // Test the normal case
  public void testNormalCase() throws IOException {
    Directory dir = newDirectory();

    IndexWriter writer =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMaxBufferedDocs(10)
                .setMergePolicy(new MockMergePolicy()));

    for (int i = 0; i < 100; i++) {
      addDoc(writer);
      checkInvariants(writer);
    }

    writer.close();
    dir.close();
  }

  // Test to see if there is over merge
  public void testNoOverMerge() throws IOException {
    Directory dir = newDirectory();

    IndexWriter writer =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMaxBufferedDocs(10)
                .setMergePolicy(new MockMergePolicy()));

    boolean noOverMerge = false;
    for (int i = 0; i < 100; i++) {
      addDoc(writer);
      checkInvariants(writer);
      if (writer.getNumBufferedDocuments() + writer.getSegmentCount() >= 18) {
        noOverMerge = true;
      }
    }
    assertTrue(noOverMerge);

    writer.close();
    dir.close();
  }

  // Test the case where flush is forced after every addDoc
  public void testForceFlush() throws IOException {
    Directory dir = newDirectory();

    MockMergePolicy mp = new MockMergePolicy();
    mp.setMergeFactor(10);
    IndexWriter writer =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMaxBufferedDocs(10)
                .setMergePolicy(mp));

    for (int i = 0; i < 100; i++) {
      addDoc(writer);
      writer.flush();
    }

    writer.close();
    dir.close();
  }

  // Test the case where mergeFactor changes
  public void testMergeFactorChange() throws IOException {
    Directory dir = newDirectory();

    IndexWriter writer =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMaxBufferedDocs(10)
                .setMergePolicy(new MockMergePolicy())
                .setMergeScheduler(new SerialMergeScheduler()));

    for (int i = 0; i < 250; i++) {
      addDoc(writer);
      checkInvariants(writer);
    }

    ((MockMergePolicy) writer.getConfig().getMergePolicy()).setMergeFactor(5);

    // merge policy only fixes segments on levels where merges
    // have been triggered, so check invariants after all adds
    for (int i = 0; i < 10; i++) {
      addDoc(writer);
    }
    checkInvariants(writer);

    writer.close();
    dir.close();
  }

  // Test the case where both mergeFactor and maxBufferedDocs change
  @Nightly
  public void testMaxBufferedDocsChange() throws IOException {
    Directory dir = newDirectory();

    IndexWriter writer =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMaxBufferedDocs(101)
                .setMergePolicy(new MockMergePolicy())
                .setMergeScheduler(new SerialMergeScheduler()));

    // leftmost* segment has 1 doc
    // rightmost* segment has 100 docs
    for (int i = 1; i <= 100; i++) {
      for (int j = 0; j < i; j++) {
        addDoc(writer);
        checkInvariants(writer);
      }
      writer.close();

      writer =
          new IndexWriter(
              dir,
              newIndexWriterConfig(new MockAnalyzer(random()))
                  .setOpenMode(OpenMode.APPEND)
                  .setMaxBufferedDocs(101)
                  .setMergePolicy(new MockMergePolicy())
                  .setMergeScheduler(new SerialMergeScheduler()));
    }

    writer.close();
    MockMergePolicy ldmp = new MockMergePolicy();
    ldmp.setMergeFactor(10);
    writer =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setOpenMode(OpenMode.APPEND)
                .setMaxBufferedDocs(10)
                .setMergePolicy(ldmp)
                .setMergeScheduler(new SerialMergeScheduler()));

    // merge policy only fixes segments on levels where merges
    // have been triggered, so check invariants after all adds
    for (int i = 0; i < 100; i++) {
      addDoc(writer);
    }
    checkInvariants(writer);

    for (int i = 100; i < 1000; i++) {
      addDoc(writer);
    }
    writer.commit();
    writer.waitForMerges();
    writer.commit();
    checkInvariants(writer);

    writer.close();
    dir.close();
  }

  // Test the case where a merge results in no doc at all
  public void testMergeDocCount0() throws IOException {
    Directory dir = newDirectory();

    MockMergePolicy ldmp = new MockMergePolicy();
    ldmp.setMergeFactor(100);
    IndexWriter writer =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMaxBufferedDocs(10)
                .setMergePolicy(ldmp));

    for (int i = 0; i < 250; i++) {
      addDoc(writer);
      checkInvariants(writer);
    }
    writer.close();

    // delete some docs without merging
    writer =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE));
    writer.deleteDocuments(new Term("content", "aaa"));
    writer.close();

    ldmp = new MockMergePolicy();
    ldmp.setMergeFactor(5);
    writer =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setOpenMode(OpenMode.APPEND)
                .setMaxBufferedDocs(10)
                .setMergePolicy(ldmp)
                .setMergeScheduler(new ConcurrentMergeScheduler()));

    // merge factor is changed, so check invariants after all adds
    for (int i = 0; i < 10; i++) {
      addDoc(writer);
    }
    writer.commit();
    writer.waitForMerges();
    writer.commit();
    checkInvariants(writer);
    assertEquals(10, writer.getDocStats().maxDoc);

    writer.close();
    dir.close();
  }

  private void addDoc(IndexWriter writer) throws IOException {
    Document doc = new Document();
    doc.add(newTextField("content", "aaa", Field.Store.NO));
    writer.addDocument(doc);
  }

  private void checkInvariants(IndexWriter writer) throws IOException {
    writer.waitForMerges();
    int maxBufferedDocs = writer.getConfig().getMaxBufferedDocs();
    int mergeFactor = ((MockMergePolicy) writer.getConfig().getMergePolicy()).getMergeFactor();

    int ramSegmentCount = writer.getNumBufferedDocuments();
    assertTrue(ramSegmentCount < maxBufferedDocs);

    int lowerBound = -1;
    int upperBound = maxBufferedDocs;
    int numSegments = 0;

    int segmentCount = writer.getSegmentCount();
    for (int i = segmentCount - 1; i >= 0; i--) {
      int docCount = writer.maxDoc(i);
      assertTrue(
          "docCount="
              + docCount
              + " lowerBound="
              + lowerBound
              + " upperBound="
              + upperBound
              + " i="
              + i
              + " segmentCount="
              + segmentCount
              + " index="
              + writer.segString()
              + " config="
              + writer.getConfig(),
          docCount > lowerBound);

      if (docCount <= upperBound) {
        numSegments++;
      } else {
        assertTrue(
            "numSegments="
                + numSegments
                + "; upperBound="
                + upperBound
                + "; mergeFactor="
                + mergeFactor
                + "; segs="
                + writer.segString()
                + " config="
                + writer.getConfig(),
            numSegments < mergeFactor);

        do {
          lowerBound = upperBound;
          upperBound *= mergeFactor;
        } while (docCount > upperBound);
        numSegments = 1;
      }
    }
    assertTrue(numSegments < mergeFactor);
  }

  private static final double EPSILON = 1E-14;

  public void testSetters() {
    assertSetters(new LogByteSizeMergePolicy());
    assertSetters(new MockMergePolicy());
  }

  // Test basic semantics of merge on commit
  public void testMergeOnCommit() throws IOException {
    Directory dir = newDirectory();

    IndexWriter firstWriter =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE));
    for (int i = 0; i < 5; i++) {
      TestIndexWriter.addDoc(firstWriter);
      firstWriter.flush();
    }
    DirectoryReader firstReader = DirectoryReader.open(firstWriter);
    assertEquals(5, firstReader.leaves().size());
    firstReader.close();
    firstWriter.close(); // When this writer closes, it does not merge on commit.

    IndexWriterConfig iwc =
        newIndexWriterConfig(new MockAnalyzer(random()))
            .setMergePolicy(new MergeOnXMergePolicy(newMergePolicy(), MergeTrigger.COMMIT))
            .setMaxFullFlushMergeWaitMillis(Integer.MAX_VALUE);

    IndexWriter writerWithMergePolicy = new IndexWriter(dir, iwc);
    writerWithMergePolicy.commit(); // No changes. Commit doesn't trigger a merge.

    DirectoryReader unmergedReader = DirectoryReader.open(writerWithMergePolicy);
    assertEquals(5, unmergedReader.leaves().size());
    unmergedReader.close();

    TestIndexWriter.addDoc(writerWithMergePolicy);
    writerWithMergePolicy.commit(); // Doc added, do merge on commit.
    assertEquals(1, writerWithMergePolicy.getSegmentCount()); //

    DirectoryReader mergedReader = DirectoryReader.open(writerWithMergePolicy);
    assertEquals(1, mergedReader.leaves().size());
    mergedReader.close();

    try (IndexReader reader = DirectoryReader.open(writerWithMergePolicy)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      assertEquals(6, reader.numDocs());
      assertEquals(6, searcher.count(new MatchAllDocsQuery()));
    }

    writerWithMergePolicy.close();
    dir.close();
  }

  // Test basic semantics of merge on commit and events recording invocation
  public void testMergeOnCommitWithEventListener() throws IOException {
    Directory dir = newDirectory();

    IndexWriter firstWriter =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE));
    for (int i = 0; i < 5; i++) {
      TestIndexWriter.addDoc(firstWriter);
      firstWriter.flush();
    }
    DirectoryReader firstReader = DirectoryReader.open(firstWriter);
    assertEquals(5, firstReader.leaves().size());
    firstReader.close();
    firstWriter.close(); // When this writer closes, it does not merge on commit.

    MockIndexWriterEventListener eventListener = new MockIndexWriterEventListener();

    IndexWriterConfig iwc =
        newIndexWriterConfig(new MockAnalyzer(random()))
            .setMergePolicy(new MergeOnXMergePolicy(newMergePolicy(), MergeTrigger.COMMIT))
            .setMaxFullFlushMergeWaitMillis(Integer.MAX_VALUE)
            .setIndexWriterEventListener(eventListener);

    IndexWriter writerWithMergePolicy = new IndexWriter(dir, iwc);
    writerWithMergePolicy.commit(); // No changes. Commit doesn't trigger a merge.

    DirectoryReader unmergedReader = DirectoryReader.open(writerWithMergePolicy);
    assertEquals(5, unmergedReader.leaves().size());
    unmergedReader.close();

    TestIndexWriter.addDoc(writerWithMergePolicy);

    assertFalse(eventListener.isEventsRecorded());
    writerWithMergePolicy.commit(); // Doc added, do merge on commit.
    assertEquals(1, writerWithMergePolicy.getSegmentCount()); //
    assertTrue(eventListener.isEventsRecorded());

    writerWithMergePolicy.close();
    dir.close();
  }

  private void assertSetters(MergePolicy lmp) {
    lmp.setMaxCFSSegmentSizeMB(2.0);
    assertEquals(2.0, lmp.getMaxCFSSegmentSizeMB(), EPSILON);

    lmp.setMaxCFSSegmentSizeMB(Double.POSITIVE_INFINITY);
    assertEquals(
        Long.MAX_VALUE / 1024 / 1024., lmp.getMaxCFSSegmentSizeMB(), EPSILON * Long.MAX_VALUE);

    lmp.setMaxCFSSegmentSizeMB(Long.MAX_VALUE / 1024 / 1024.);
    assertEquals(
        Long.MAX_VALUE / 1024 / 1024., lmp.getMaxCFSSegmentSizeMB(), EPSILON * Long.MAX_VALUE);

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          lmp.setMaxCFSSegmentSizeMB(-2.0);
        });

    // TODO: Add more checks for other non-double setters!
  }

  public void testCarryOverNewDeletesOnCommit() throws IOException, InterruptedException {
    try (Directory directory = newDirectory()) {
      boolean useSoftDeletes = random().nextBoolean();
      CountDownLatch waitForMerge = new CountDownLatch(1);
      CountDownLatch waitForUpdate = new CountDownLatch(1);
      try (IndexWriter writer =
          new IndexWriter(
              directory,
              newIndexWriterConfig()
                  .setMergePolicy(
                      new MergeOnXMergePolicy(NoMergePolicy.INSTANCE, MergeTrigger.COMMIT))
                  .setMaxFullFlushMergeWaitMillis(30 * 1000)
                  .setSoftDeletesField("soft_delete")
                  .setMaxBufferedDocs(Integer.MAX_VALUE)
                  .setRAMBufferSizeMB(100)
                  .setMergeScheduler(new ConcurrentMergeScheduler())) {
            @Override
            protected void merge(MergePolicy.OneMerge merge) throws IOException {
              waitForMerge.countDown();
              try {
                waitForUpdate.await();
              } catch (InterruptedException e) {
                throw new AssertionError(e);
              }
              super.merge(merge);
            }
          }) {

        Document d1 = new Document();
        d1.add(new StringField("id", "1", Field.Store.NO));
        Document d2 = new Document();
        d2.add(new StringField("id", "2", Field.Store.NO));
        Document d3 = new Document();
        d3.add(new StringField("id", "3", Field.Store.NO));
        writer.addDocument(d1);
        writer.flush();
        writer.addDocument(d2);
        boolean addThreeDocs = random().nextBoolean();
        int expectedNumDocs = 2;
        if (addThreeDocs) { // sometimes add another doc to ensure we don't have a fully deleted
          // segment
          expectedNumDocs = 3;
          writer.addDocument(d3);
        }
        Thread t =
            new Thread(
                () -> {
                  try {
                    waitForMerge.await();
                    if (useSoftDeletes) {
                      writer.softUpdateDocument(
                          new Term("id", "2"), d2, new NumericDocValuesField("soft_delete", 1));
                    } else {
                      writer.updateDocument(new Term("id", "2"), d2);
                    }
                    writer.flush();
                  } catch (Exception e) {
                    throw new AssertionError(e);
                  } finally {
                    waitForUpdate.countDown();
                  }
                });
        t.start();
        writer.commit();
        t.join();
        try (DirectoryReader open =
            new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(directory), "soft_delete")) {
          assertEquals(expectedNumDocs, open.numDocs());
          assertEquals("we should not have any deletes", expectedNumDocs, open.maxDoc());
        }

        try (DirectoryReader open = DirectoryReader.open(writer)) {
          assertEquals(expectedNumDocs, open.numDocs());
          assertEquals("we should not have one delete", expectedNumDocs + 1, open.maxDoc());
        }
      }
    }
  }

  /**
   * This test makes sure we release the merge readers on abort. MDW will fail if it can't close all
   * files
   */
  public void testAbortMergeOnCommit() throws IOException, InterruptedException {
    abortMergeOnX(false);
  }

  public void testAbortMergeOnGetReader() throws IOException, InterruptedException {
    abortMergeOnX(true);
  }

  void abortMergeOnX(boolean useGetReader) throws IOException, InterruptedException {
    try (Directory directory = newDirectory()) {
      CountDownLatch waitForMerge = new CountDownLatch(1);
      CountDownLatch waitForDeleteAll = new CountDownLatch(1);
      try (IndexWriter writer =
          new IndexWriter(
              directory,
              newIndexWriterConfig()
                  .setMergePolicy(
                      new MergeOnXMergePolicy(
                          newMergePolicy(),
                          useGetReader ? MergeTrigger.GET_READER : MergeTrigger.COMMIT))
                  .setMaxFullFlushMergeWaitMillis(30 * 1000)
                  .setMergeScheduler(
                      new SerialMergeScheduler() {
                        @Override
                        public synchronized void merge(
                            MergeSource mergeSource, MergeTrigger trigger) throws IOException {
                          waitForMerge.countDown();
                          try {
                            waitForDeleteAll.await();
                          } catch (InterruptedException e) {
                            throw new AssertionError(e);
                          }
                          super.merge(mergeSource, trigger);
                        }
                      }))) {

        Document d1 = new Document();
        d1.add(new StringField("id", "1", Field.Store.NO));
        Document d2 = new Document();
        d2.add(new StringField("id", "2", Field.Store.NO));
        Document d3 = new Document();
        d3.add(new StringField("id", "3", Field.Store.NO));
        writer.addDocument(d1);
        writer.flush();
        writer.addDocument(d2);
        Thread t =
            new Thread(
                () -> {
                  boolean success = false;
                  try {
                    if (useGetReader) {
                      DirectoryReader.open(writer).close();
                    } else {
                      writer.commit();
                    }
                    success = true;
                  } catch (IOException e) {
                    throw new AssertionError(e);
                  } finally {
                    if (success == false) {
                      waitForMerge.countDown();
                    }
                  }
                });
        t.start();
        waitForMerge.await();
        writer.deleteAll();
        waitForDeleteAll.countDown();
        t.join();
      }
    }
  }

  public void testForceMergeWhileGetReader() throws IOException, InterruptedException {
    try (Directory directory = newDirectory()) {
      CountDownLatch waitForMerge = new CountDownLatch(1);
      CountDownLatch waitForForceMergeCalled = new CountDownLatch(1);
      try (IndexWriter writer =
          new IndexWriter(
              directory,
              newIndexWriterConfig()
                  .setMergePolicy(
                      new MergeOnXMergePolicy(newMergePolicy(), MergeTrigger.GET_READER))
                  .setMaxFullFlushMergeWaitMillis(30 * 1000)
                  .setMergeScheduler(
                      new SerialMergeScheduler() {
                        @Override
                        public void merge(MergeSource mergeSource, MergeTrigger trigger)
                            throws IOException {
                          waitForMerge.countDown();
                          try {
                            waitForForceMergeCalled.await();
                          } catch (InterruptedException e) {
                            throw new AssertionError(e);
                          }
                          super.merge(mergeSource, trigger);
                        }
                      }))) {
        Document d1 = new Document();
        d1.add(new StringField("id", "1", Field.Store.NO));
        writer.addDocument(d1);
        writer.flush();
        Document d2 = new Document();
        d2.add(new StringField("id", "2", Field.Store.NO));
        writer.addDocument(d2);
        Thread t =
            new Thread(
                () -> {
                  try (DirectoryReader reader = DirectoryReader.open(writer)) {
                    assertEquals(2, reader.maxDoc());
                  } catch (IOException e) {
                    throw new AssertionError(e);
                  }
                });
        t.start();
        waitForMerge.await();
        Document d3 = new Document();
        d3.add(new StringField("id", "3", Field.Store.NO));
        writer.addDocument(d3);
        waitForForceMergeCalled.countDown();
        writer.forceMerge(1);
        t.join();
      }
    }
  }

  public void testFailAfterMergeCommitted() throws IOException {
    try (Directory directory = newDirectory()) {
      AtomicBoolean mergeAndFail = new AtomicBoolean(false);
      try (IndexWriter writer =
          new IndexWriter(
              directory,
              newIndexWriterConfig()
                  .setMergePolicy(
                      new MergeOnXMergePolicy(NoMergePolicy.INSTANCE, MergeTrigger.GET_READER))
                  .setMaxFullFlushMergeWaitMillis(30 * 1000)
                  .setMergeScheduler(new SerialMergeScheduler())) {
            @Override
            protected void doAfterFlush() throws IOException {
              if (mergeAndFail.get() && hasPendingMerges()) {
                executeMerge(MergeTrigger.GET_READER);
                throw new RuntimeException("boom");
              }
            }
          }) {
        Document d1 = new Document();
        d1.add(new StringField("id", "1", Field.Store.NO));
        writer.addDocument(d1);
        writer.flush();
        Document d2 = new Document();
        d2.add(new StringField("id", "2", Field.Store.NO));
        writer.addDocument(d2);
        writer.flush();
        mergeAndFail.set(true);
        try (DirectoryReader reader = DirectoryReader.open(writer)) {
          assertNotNull(reader); // make compiler happy and use the reader
          fail();
        } catch (RuntimeException e) {
          assertEquals("boom", e.getMessage());
        } finally {
          mergeAndFail.set(false);
        }
      }
    }
  }

  public void testStressUpdateSameDocumentWithMergeOnGetReader()
      throws IOException, InterruptedException {
    stressUpdateSameDocumentWithMergeOnX(true);
  }

  public void testStressUpdateSameDocumentWithMergeOnCommit()
      throws IOException, InterruptedException {
    stressUpdateSameDocumentWithMergeOnX(false);
  }

  void stressUpdateSameDocumentWithMergeOnX(boolean useGetReader)
      throws IOException, InterruptedException {
    try (Directory directory = newDirectory()) {
      try (RandomIndexWriter writer =
          new RandomIndexWriter(
              random(),
              directory,
              newIndexWriterConfig()
                  .setMergePolicy(
                      new MergeOnXMergePolicy(
                          newMergePolicy(),
                          useGetReader ? MergeTrigger.GET_READER : MergeTrigger.COMMIT))
                  .setMaxFullFlushMergeWaitMillis(10 + random().nextInt(2000))
                  .setSoftDeletesField("soft_delete")
                  .setMergeScheduler(new ConcurrentMergeScheduler()))) {
        Document d1 = new Document();
        d1.add(new StringField("id", "1", Field.Store.NO));
        writer.updateDocument(new Term("id", "1"), d1);
        writer.commit();

        AtomicInteger iters = new AtomicInteger(100 + random().nextInt(TEST_NIGHTLY ? 5000 : 1000));
        AtomicInteger numFullFlushes =
            new AtomicInteger(10 + random().nextInt(TEST_NIGHTLY ? 500 : 100));
        AtomicBoolean done = new AtomicBoolean(false);
        Thread[] threads = new Thread[1 + random().nextInt(4)];
        for (int i = 0; i < threads.length; i++) {
          Thread t =
              new Thread(
                  () -> {
                    try {
                      while (iters.decrementAndGet() > 0 || numFullFlushes.get() > 0) {
                        writer.updateDocument(new Term("id", "1"), d1);
                        if (random().nextBoolean()) {
                          writer.addDocument(new Document());
                        }
                      }
                    } catch (Exception e) {
                      throw new AssertionError(e);
                    } finally {
                      done.set(true);
                    }
                  });
          t.start();
          threads[i] = t;
        }
        try {
          while (done.get() == false) {
            if (useGetReader) {
              try (DirectoryReader reader = writer.getReader()) {
                assertEquals(
                    1,
                    new IndexSearcher(reader)
                        .search(new TermQuery(new Term("id", "1")), 10)
                        .totalHits
                        .value);
              }
            } else {
              if (random().nextBoolean()) {
                writer.commit();
              }
              try (DirectoryReader delegate = DirectoryReader.open(directory);
                  DirectoryReader open =
                      new SoftDeletesDirectoryReaderWrapper(delegate, "___soft_deletes")) {
                assertEquals(
                    1,
                    new IndexSearcher(open)
                        .search(new TermQuery(new Term("id", "1")), 10)
                        .totalHits
                        .value);
              }
            }
            numFullFlushes.decrementAndGet();
          }
        } finally {
          numFullFlushes.set(0);
          for (Thread t : threads) {
            t.join();
          }
        }
      }
    }
  }

  // Test basic semantics of merge on getReader
  public void testMergeOnGetReader() throws IOException {
    Directory dir = newDirectory();

    IndexWriter firstWriter =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE));
    for (int i = 0; i < 5; i++) {
      TestIndexWriter.addDoc(firstWriter);
      firstWriter.flush();
    }
    DirectoryReader firstReader = DirectoryReader.open(firstWriter);
    assertEquals(5, firstReader.leaves().size());
    firstReader.close();
    firstWriter.close(); // When this writer closes, it does not merge on commit.

    IndexWriterConfig iwc =
        newIndexWriterConfig(new MockAnalyzer(random()))
            .setMergePolicy(new MergeOnXMergePolicy(newMergePolicy(), MergeTrigger.GET_READER))
            .setMaxFullFlushMergeWaitMillis(Integer.MAX_VALUE);

    IndexWriter writerWithMergePolicy = new IndexWriter(dir, iwc);

    try (DirectoryReader unmergedReader =
        DirectoryReader.open(dir)) { // No changes. GetReader doesn't trigger a merge.
      assertEquals(5, unmergedReader.leaves().size());
    }

    TestIndexWriter.addDoc(writerWithMergePolicy);
    try (DirectoryReader mergedReader = DirectoryReader.open(writerWithMergePolicy)) {
      // Doc added, do merge on getReader.
      assertEquals(1, mergedReader.leaves().size());
    }

    writerWithMergePolicy.close();
    dir.close();
  }

  private static class MergeOnXMergePolicy extends FilterMergePolicy {
    private final MergeTrigger trigger;

    private MergeOnXMergePolicy(MergePolicy in, MergeTrigger trigger) {
      super(in);
      this.trigger = trigger;
    }

    @Override
    public MergeSpecification findFullFlushMerges(
        MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext) {
      // Optimize down to a single segment on commit
      if (mergeTrigger == trigger && segmentInfos.size() > 1) {
        List<SegmentCommitInfo> nonMergingSegments = new ArrayList<>();
        for (SegmentCommitInfo sci : segmentInfos) {
          if (mergeContext.getMergingSegments().contains(sci) == false) {
            nonMergingSegments.add(sci);
          }
        }
        if (nonMergingSegments.size() > 1) {
          MergeSpecification mergeSpecification = new MergeSpecification();
          mergeSpecification.add(new OneMerge(nonMergingSegments));
          return mergeSpecification;
        }
      }
      return null;
    }
  }
}
