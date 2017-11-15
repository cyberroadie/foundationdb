/*
 * VersionstampSmokeTest.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2013-2018 Apple Inc. and the FoundationDB project authors
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

package com.apple.foundationdb.test;

import com.apple.foundationdb.Database;
import com.apple.foundationdb.FDB;
import com.apple.foundationdb.MutationType;
import com.apple.foundationdb.Transaction;
import com.apple.foundationdb.async.Function;
import com.apple.foundationdb.async.Future;
import com.apple.foundationdb.subspace.Subspace;
import com.apple.foundationdb.tuple.Tuple;
import com.apple.foundationdb.tuple.Versionstamp;

public class VersionstampSmokeTest {
    public static void main(String[] args) {
        FDB fdb = FDB.selectAPIVersion(510);
        Database db = fdb.open();

        db.run(new Function<Transaction, Void>() {
            @Override
            public Void apply(Transaction tr) {
                tr.clear(Tuple.from("prefix").range());
                return null;
            }
        });

        Future<byte[]> trVersionFuture = db.run(new Function<Transaction, Future<byte[]>>() {
            @Override
            public Future<byte[]> apply(Transaction tr) {
                // The incomplete Versionstamp will have tr's version information when committed.
                Tuple t = Tuple.from("prefix", Versionstamp.incomplete());
                tr.mutate(MutationType.SET_VERSIONSTAMPED_KEY, t.packWithVersionstamp(), new byte[0]);
                return tr.getVersionstamp();
            }
        });

        byte[] trVersion = trVersionFuture.get();

        Versionstamp v = db.run(new Function<Transaction, Versionstamp>() {
            @Override
            public Versionstamp apply(Transaction tr) {
                Subspace subspace = new Subspace(Tuple.from("prefix"));
                byte[] serialized = tr.getRange(subspace.range(), 1).iterator().next().getKey();
                Tuple t = subspace.unpack(serialized);
                return t.getVersionstamp(0);
            }
        });

        assert v.equals(Versionstamp.complete(trVersion));
    }
}
