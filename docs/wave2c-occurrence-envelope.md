# Wave 2C activity occurrence envelope

Mirrors website PR #394. The payload contains only version, capture UUID,
fixed activity ID, and the observed counter. No chat text, name, quantity,
game-value ID or arbitrary instruction. A counter is occurrence identity and
must never be awarded as a lifetime total. The future caller must first
establish a valid consecutive signal in an eligible account/world/rule epoch,
then reuse the original UUID and envelope for all retries. No live network,
capture queue, rule discovery, or detector registration in this slice.
Actual gameplay verification and server-side eligibility/replay enforcement
remain prerequisites for activation.
