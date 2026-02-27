# CS4632_restaurant_simulation
Discrete-event simulation of a restaurant service system (CS4632)
Project Overview
This project implements a discrete-event simulation of a restaurant service system for CS 4632 (Modeling and Simulation). The goal of the simulation is to model how customers arrive at a restaurant, wait in a queue if necessary, receive service from available servers, and eventually depart the system.

The simulation focuses on key components commonly found in service systems, including customers, queues, servers, and an event list to manage arrival and service completion events. By modeling these components and their interactions over time, the simulation can be used to study system performance such as customer waiting time, server utilization, and overall time spent in the system.

To keep the model manageable, the simulation makes reasonable simplifying assumptions, such as fixed service behavior and a limited number of servers. The project emphasizes clear system structure, event-driven logic, and alignment with standard modeling concepts such as queueing theory and discrete-event simulation.

This repository contains the project documentation, UML diagrams, and source code that will be developed incrementally throughout the course.
## Repository Structure

- `src/` – Source code for the simulation  
- `diagrams/` – UML diagrams (class and sequence diagrams)  
- `README.md` – Project overview and description  

## Project Status

### What’s Implemented So Far
- Core discrete-event simulation engine
- Customer, Server, Queue, and Event classes
- Arrival and service completion event handling
- Performance metrics collection:
  - Average wait time
  - Max wait time
  - Average time in system
  - Average queue length (Lq)
  - Average number in system (L)
  - Server utilization
  - Little’s Law validation

### What’s Still to Come
- Improve output formatting
- Additional experimental comparisons
- Final UML diagram upload

### Changes from Original Proposal
- Originally planned in Python
- Switched to Java for stronger object-oriented structure and easier implementation of matematical formulas


## Installation Instructions

### Dependencies
- Java JDK 17+
- IntelliJ IDEA (recommended)

No external libraries required.

### How to Run (IntelliJ)
1. Open project in IntelliJ
2. Open `src/Simulation.java`
3. Click Run
4. Enter inputs when prompted

## Usage

### How to Run the Simulation

The program prompts the user for the following inputs:

- Number of servers
- Simulation duration (in hours)
- Customer arrival rate (customers per hour)
- Average service time (in minutes)

After execution, the system prints performance metrics.

### Expected Output / Behavior

The simulation outputs:

- Total arrivals
- Total customers served
- Average wait time
- Maximum wait time
- Average time in system
- Average queue length (Lq)
- Maximum queue length
- Average number in system (L)
- Server utilization (u)
- Little’s Law comparison (Observed L vs λW)


## Architecture Overview

### Main Components

**Simulation.java**  
Controls the main event loop, schedules events, and collects performance metrics.

**Customer.java**  
Represents a customer entity and stores arrival and service-related data.

**Server.java**  
Represents a server and tracks busy/idle state and utilization time.

**CustomerQueue.java**  
Implements a FIFO queue structure for waiting customers.

**Event.java**  
Represents discrete events (arrival or departure) within the system.


### Design Approach

The system follows a discrete-event simulation structure:

- Customers generate arrival events
- Servers process service completion events
- An event list controls system progression over time
- Metrics are collected continuously during simulation execution

The architecture directly maps to the UML design and follows object-oriented modeling principles.
