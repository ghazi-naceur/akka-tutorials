
### Event Sourcing

- Instead of storing the current state, we'll store "events".
- We can always recreate the current state by replaying events.
- This model is central to Akka Persistence.

### Pros of Event Sourcing

- High performance: Events are only appended
- Avoids relational stores and ORM entirely
- Full trace of every state
- Fits the Akka Actor model perfectly

### Cons of Event Sourcing

- querying a state potentially expensive (is sorted out by **Akka Persistence Query**)
- potential performance issues with long-lived entities (is sorted out by **Snapshotting**)
- data model subject to change (is sorted out by **Schema Evolution**)
- just a very different model

### Persistent Actor

- can do everything a normal Actor can do:
  * send and receive messages
  * hold internal state
  * run in parallel with many other actors

- has extra capabilities:
  * has a **persistence ID**
  * persist events to a long-term store
  * recover state by replaying events from the store

- When a Persistent Actor handles a ~~message~~ **command**:
  * it can asynchronously persist an event to the store
  * after the vent is persisted, it changes its internal state

- When a Persistent Actor starts/restarts
  * it replays all events with its **persistence ID**

### Workflow

#### Normal workflow

1- A command is dequeued from the mailbox to be handled by the Actor

2- Actor can persist asynchronously a command to the Persistent Store or Journal

3- After the Journal has finished, the Actor can change its state and/or send messages to other actors

4- The command is discarded

#### Workflow when Actor start/restart

1- Before handling any command whatsoever, the Persistent Actor will automatically query the Journal for all 
events associated to his **Persistent ID**

2- If the Journal has events for it, they will be played to the Actor in the same order they were persisted 
and the actor will change its state as a result

3- The actor is free to receive further messages. If the Actor receives commands in the recovery phase, they 
will be simply stashed until the recovery has completed 