# Payments queue backlog

Applies to: **payments-queue** (SQS) and its consumer **payments-worker**.
Owner: Payments team.

## Symptoms

- `ApproximateAgeOfOldestMessage` keeps growing; thousands of visible messages.
- payments-worker logs `Consumer lag growing`; worker CPU near 100%.
- checkout-api waits longer for payment confirmation, so its latency rises slowly.

## Verify

1. Compare the incoming message rate with the processing rate. If incoming is higher, the
   backlog will keep growing whatever else you do.
2. Check whether payments-worker pods are running and not crash-looping.
3. Check the dead-letter queue for poison messages being retried over and over.
4. Check downstream calls from the worker (orders-db, payment provider) for slowness.

## Safe mitigation

- Scale payments-worker out (more replicas) if CPU-bound and the downstream can take the load.
- If a poison message blocks progress, move it to the dead-letter queue; do not delete messages.
- Never purge the queue: each message is a customer payment.

## Long-term fixes

- Autoscale payments-worker on queue depth, not CPU.
- Set `maxReceiveCount` so poison messages reach the dead-letter queue quickly.
- Alert on message age above 2 minutes.
