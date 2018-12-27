// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

#ifndef DORIS_BE_SRC_OLAP_ROWSET_ROWSET_H
#define DORIS_BE_SRC_OLAP_ROWSET_ROWSET_H

#include "olap/new_status.h"
#include "olap/rowset/rowset_reader.h"
#include "olap/rowset/rowset_builder.h"
#include "olap/rowset/rowset_meta.h"

#include <memory>

namespace doris {

class RowsetBuilder;
class Rowset;
using RowsetSharedPtr = std::shared_ptr<Rowset>;

class Rowset {
public:
    virtual ~Rowset() { }

    virtual NewStatus init(const RowsetMeta& rowset_meta) = 0;

    virtual std::unique_ptr<RowsetReader> create_reader() = 0;

    virtual NewStatus copy(RowsetBuilder* dest_rowset_builder) = 0;

    virtual NewStatus remove() = 0;
};

} // namespace doris

#endif // DORIS_BE_SRC_OLAP_ROWSET_ROWSET_H