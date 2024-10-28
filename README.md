# Top Trading Cycle (TTC) Algorithm for Housing Allocation

This Jupyter Notebook implements the **Top Trading Cycle (TTC) algorithm** to solve a housing allocation problem. In this scenario, agents are matched to houses based on strict preferences, aiming to achieve a **Pareto optimal matching** where no group of agents can improve their match by exchanging houses among themselves. The notebook also provides a visual representation of the final house assignments.

## Problem Overview

The TTC algorithm is used to find a **core-stable allocation** in a housing market where:
1. Each agent has an initial house.
2. Each agent has a preference list of houses they would like to trade for.
3. The algorithm identifies trading cycles and matches agents to their preferred houses in a way that is both **Pareto optimal** and **core stable**.

## Requirements

- **Python 3.6+**
- **Jupyter Notebook**
- **Libraries**:
  - `networkx` and `matplotlib` for graph visualization.

Install the required libraries if needed:

```bash
pip install networkx matplotlib
```

## Notebook Contents

The notebook is divided into several sections, each performing a different part of the TTC algorithm:

1. **Define Classes**:
   - **`House`**: Represents each unique house.
   - **`Agent`**: Represents each agent with a unique ID, an initial house, and a list of preferred houses.

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


## Usage Instructions

1. **Open the Notebook**:
   - Run `jupyter notebook` in your terminal, navigate to the directory containing the notebook, and open `Top_Trading_Cycle_Algorithm.ipynb`.

2. **Execute the Cells**:
   - Run each cell sequentially to:
     - Define the `House` and `Agent` classes.
     - Initialize the agents and houses.
     - Build the top choice graph and perform trades.
     - Run the TTC algorithm.
     - Visualize the final house assignments.

3. **View the Results**:
   - The final output includes a list of agents with their assigned houses, followed by a visualization showing the final assignment of each agent to their house in a directed graph.

### Example Output

Running the final cell will produce output like:

```plaintext
Agent 1 ends up with House(3)
Agent 2 ends up with House(1)
Agent 3 ends up with House(4)
```

## Explanation of the Algorithm

1. **Top Choice Graph**:
   - Each agent points to their top-choice house held by another agent. This forms a directed graph where edges represent an agent's top choice among the currently available houses.

2. **Cycle Detection**:
   - Cycles are detected within the graph, representing groups of agents that can exchange houses among themselves to reach their top preferences.

3. **Trade Execution**:
   - Trades are made within each detected cycle. Agents in a cycle receive their top-choice house from another agent in the same cycle. After each cycle, the houses involved are removed from further consideration.

4. **Visualization**:
   - After all cycles have been processed and each agent has received a house, the final assignments are displayed in a directed graph, showing the end allocation of houses to agents.
