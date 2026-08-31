# W8 admission decision

One Lua invocation atomically checks duplicate identity, counts PENDING work, and inserts only accepted work. Capacity is 3; a duplicate consumes no slot; COMPLETED/FAILED removes the pending key and permits re-entry; rejected work is not written as accepted state. Redis connection failure returns REJECTED. Two simultaneous requests for the final slot accept exactly one. A `>` mutation at the capacity boundary made both oracles Red before restoration.
