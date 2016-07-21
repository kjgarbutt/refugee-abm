package refugee;

import java.awt.Color;
import ec.util.MersenneTwisterFast;
import java.util.ArrayList;
import java.util.HashMap;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.util.Bag;
import sim.util.Double2D;
import sim.util.Int2D;

class RefugeeFamily implements Steppable {
	private Int2D location;
	private ArrayList<Refugee> familyMembers;
	private Route route;
	private int routePosition;
	private double finStatus;
	private City home;
	private City currentCity;
	private City goal;
	MersenneTwisterFast random;

	public RefugeeFamily(Int2D location, int size, City home, double finStatus) {
		this.location = location;
		this.home = home;
		this.goal = home;
		this.finStatus = finStatus;
		familyMembers = new ArrayList<Refugee>();
		currentCity = home;
		// routePosition = 0;
	}

	@Override
	public void step(SimState state) {
		random = new MersenneTwisterFast();
		// System.out.println("here");
		Migration migrationSim = (Migration) state;
		for (Refugee r : familyMembers) {
			if (r.getHealthStatus() == Constants.DEAD) {
				return;
			}
		}

		if (finStatus == 0.0) {
			return;
		} else {
			Bag cities = migrationSim.cities; // change later when cities
												// included in map

			City goalCity = calcGoalCity(cities);
			if (this.goal.getName() != goalCity.getName())
				System.out.println("Goal Changed");
			this.goal = goalCity;
			for (Object c : cities) {
				City city = (City) c;
				if (this.location == city.getLocation()) {
					currentCity = city;
				}
			}
			System.out.println("Home: " + this.getHome().getName() + " Goal " + goalCity.getName());
			 System.out.println("Current: "+ currentCity.getName());
			 //System.out.println(route);
			if (this.location != goalCity.getLocation()) {
				setGoal(currentCity, goalCity);// Astar inside here
				//System.out.println(route);
				if (route == null){
					System.out.println("No route found:");
					return;}
				//System.out.println(route);
				int index = route.getIndex(this.location);
				if (index != -1) {// if already on the route (in between cities)
					this.setLocation(route.getLocation(index + 1));
					updatePositionOnMap(migrationSim);
				} else {// new route
					Int2D nextStep = route.getLocation(1);
					this.setLocation(nextStep);
					updatePositionOnMap(migrationSim);
				}
			}
		}
	}

	public City calcGoalCity(Bag citylist) { // returns the best city
		City bestCity = null;
		double max = 0.0;
		for (Object city : citylist) {
			City c = (City) city;
			double cityDesirability = dangerCare() * c.getViolence() + familyAbroadCare() * c.getFamilyPresence()
					+ c.getEconomy() + c.getScaledPopulation();
			if (c.getRefugeePopulation() + familyMembers.size() >= c.getQuota()) // if
																					// reached
																					// quota,
																					// desirability
																					// is
																					// 0
				cityDesirability = 0;
			if (cityDesirability > max) {
				max = cityDesirability;
				bestCity = c;
			}

		}
		return bestCity;
	}

	private void setGoal(City from, City to) {
		this.goal = to;
		this.route = from.getRoute(to, this);
		this.routePosition = 0;
	}

	public void updatePositionOnMap(Migration migrationSim) {
		double randX = migrationSim.random.nextDouble();
		double randY = migrationSim.random.nextDouble();
		System.out.println("Location: " + location.getX() + " " + location.getY());
		migrationSim.world.setObjectLocation(this, new Double2D(location.getX() + randX, location.getY() + randY));
		// migrationSim.worldPopResolution.setObjectLocation(this,
		// (int)location.getX()/10, (int)location.getY()/10);
	}

	// get and set
	public Int2D getLocation() {
		return location;
	}

	public void setLocation(Int2D location) {
		this.location = location;
		for (Refugee r : familyMembers) {
			r.setLocation(location);
		}
	}

	public double getFinStatus() {
		return finStatus;
	}

	public void setFinStatus(int finStatus) {
		this.finStatus = finStatus;
	}

	public void setHome(City home) {
		this.home = home;
	}

	public City getGoal() {
		return goal;
	}

	public void setGoal(City goal) {
		this.goal = goal;
	}

	public City getHome() {
		return home;
	}

	public void setCurrent(City current) {
		this.currentCity = current;
	}

	public ArrayList<Refugee> getFamily() {
		return familyMembers;
	}

	public void setFamily(ArrayList<Refugee> family) {
		this.familyMembers = family;
	}

	public double dangerCare() {// 0-1, young, old, or has family weighted more
		double dangerCare = 0.5;
		for (Refugee r : familyMembers) {
			if (r.getAge() < 12 || r.getAge() > 60) {
				dangerCare += Parameters.DANGER_CARE_WEIGHT * random.nextDouble();
			}
		}
		return dangerCare;
	}

	public double familyAbroadCare() { // 0-1, if traveling without family,
										// cares more
		double familyCare = 1.0;
		if (this.familyMembers.size() == 1)
			familyCare += Parameters.FAMILY_ABROAD_CARE_WEIGHT * random.nextDouble();
		return familyCare;
	}

}
