# Top Trading Cycle (TTC) Algorithm for Housing Allocation

This Java implementation of the **Top Trading Cycle (TTC) algorithm** to solve a housing allocation problem. In this scenario, agents are matched to houses based on strict preferences, aiming to achieve a **Pareto optimal matching** where no group of agents can improve their match by exchanging houses among themselves.

## Current Known Issues And room for improvement
Occasionally, the algorithm will find itself in a deadlock and time out instead of completing the implementation. The probability of a deadlock occuring increases with each added client to the system. The current implementation does not account for clients joining mid execution.

## Problem Overview

The TTC algorithm is used to find a **core-stable allocation** in a housing market where:
1. Each agent has an initial house.
2. Each agent has a preference list of houses they would like to trade for.
3. The algorithm identifies trading cycles and matches agents to their preferred houses in a way that is both **Pareto optimal** and **core stable**.

## Contents

1. **Define Classes**:
   - **`Client`**: Represents each client.
   - **`Test cases Shell script`**: Runs the different docker compose files that contain the test cases for the algorithm

2. **Initialize Agents and Houses**:
   - Sets up the agents with their initial houses and preference lists.

3. **Build the Top Choice Graph**:
   - Creates a directed graph where each agent points to their top-choice house held by another agent.

4. **Find Cycles in the Graph**:
   - Detects cycles within the graph, representing groups of agents that can exchange houses to achieve their top choices.

5. **Perform Trades in Cycles**:
   - Executes trades in each detected cycle, assigning each agent their preferred house.

6. **Run the Top Trading Cycle Algorithm**:
   - This is the main function that orchestrates the cycle detection and trading steps, ultimately finding a stable matching.

7. **Visualize Final Assignments**:
   - Displays a directed graph showing each agent's final house assignment after executing the TTC algorithm.

### Example Output

Running the final cell will produce output like:

```plaintext
Client 1 | 9
Client 2 | 8
Client 3 | 4
etc...
```

## Explanation of the Algorithm

1. **Top Choice Graph**:
   - Each agent points to their top-choice house held by another agent. This forms a directed graph where edges represent an agent's top choice among the currently available houses.

2. **Cycle Detection**:
   - Cycles are detected within the graph, representing groups of agents that can exchange houses among themselves to reach their top preferences.

3. **Trade Execution**:
   - Trades are made within each detected cycle. Agents in a cycle receive their top-choice house from another agent in the same cycle. After each cycle, the houses involved are removed from further consideration.
