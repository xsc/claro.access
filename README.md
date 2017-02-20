# claro.access

__[Documentation](http://xsc.github.io/claro.access/)__

__claro.access__ is a [claro][claro] engine middleware allowing for basic
read/write access control on a per-resolvable level.

[![Build Status](https://travis-ci.org/xsc/claro.access.svg?branch=master)](https://travis-ci.org/xsc/claro.access)
[![Clojars Artifact](https://img.shields.io/clojars/v/claro/access.svg)](https://clojars.org/claro/access)

[claro]: https://github.com/xsc/claro

This library requires Clojure ≥ 1.7.0 and claro ≥ 0.2.8.

## Usage


```clojure
(require '[claro.access :as access]
         '[claro.data :as data]
         '[claro.engine :as engine])
```

### Middleware

The middleware can be attached to an engine of your choice using `wrap-access`:

```clojure
(defonce engine
  (-> (engine/engine)
      (access/wrap-access)))
```

### Read Access

By implementing the `access/Read` protocol a predicate can be added that decides
on a per-resolvable basis whether to leave it untouched or to replace it. For
example, the following resolvable can only be shown to its owner – which is not
known before resolution:

```clojure
(defrecord Conversation [id]
  data/Resolvable
  ...
  access/Read
  (can-read? [_ {:keys [session]} {:keys [owner-id]}]
    (= (:id session) owner-id)))
```

Alternatively, an error container can be returned which will then be used for
replacement (instead of `nil`). Note that `can-read?` operates on the raw,
untransformed resolution result.

### Write Access

By implementing the `access/Resolve` protocol a (potentially deferred) predicate
can be added that decides on a per-resolvable basis whether resolution should be
run or not. For example, the following mutation will only be run, if the
required permission is given within the session:

```clojure
(defrecord CreateConversation [...]
  data/Mutation
  data/Resolvable
  ...
  access/Resolve
  (can-resolve? [_ {:keys [session]}]
    (or (:email-verified? session)
        (data/error "email needs to be verified to create a conversation."))))
```

Alternatively, a deferred value can be used to fetch data facilitating the
decision, e.g.:

```clojure
(defrecord CreateConversation [...]
  data/Mutation
  data/Resolvable
  ...
  access/Resolve
  (can-resolve? [_ {:keys [db session]}]
    (d/future
      (let [permissions (fetch-permissions! db session)]
        (or (:can-create-conversation? permissions)
            (data/error "conversation creation not allowed."))))))
```

Note that, when `can-resolve?` returns false or an error container, no
resolution is attempted.

## License

```
MIT License

Copyright (c) 2017 Yannick Scherer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
