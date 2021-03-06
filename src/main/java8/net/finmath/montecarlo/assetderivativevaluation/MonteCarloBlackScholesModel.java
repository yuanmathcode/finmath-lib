/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 20.01.2004
 */
package net.finmath.montecarlo.assetderivativevaluation;

import java.time.LocalDateTime;
import java.util.Map;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.montecarlo.process.MonteCarloProcessFromProcessModel;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;

/**
 * This class glues together a <code>BlackScholeModel</code> and a Monte-Carlo implementation of a <code>MonteCarloProcessFromProcessModel</code>
 * and forms a Monte-Carlo implementation of the Black-Scholes Model by implementing <code>AssetModelMonteCarloSimulationModel</code>.
 *
 * The model is
 * \[
 * 	dS = r S dt + \sigma S dW, \quad S(0) = S_{0},
 * \]
 * \[
 * 	dN = r N dt, \quad N(0) = N_{0},
 * \]
 *
 * The class provides the model of S to an <code>{@link net.finmath.montecarlo.process.MonteCarloProcess}</code> via the specification of
 * \( f = exp \), \( \mu = r - \frac{1}{2} \sigma^2 \), \( \lambda_{1,1} = \sigma \), i.e.,
 * of the SDE
 * \[
 * 	dX = \mu dt + \lambda_{1,1} dW, \quad X(0) = \log(S_{0}),
 * \]
 * with \( S = f(X) \). See {@link net.finmath.montecarlo.process.MonteCarloProcess} for the notation.
 *
 * @author Christian Fries
 * @see net.finmath.montecarlo.process.MonteCarloProcess The interface for numerical schemes.
 * @see net.finmath.montecarlo.model.ProcessModel The interface for models provinding parameters to numerical schemes.
 * @version 1.0
 */
public class MonteCarloBlackScholesModel implements AssetModelMonteCarloSimulationModel {

	private final BlackScholesModel model;
	private final MonteCarloProcess process;

	/*
	 * The default seed
	 */
	private static final int seed = 3141;

	/**
	 * Create a Monte-Carlo simulation using given process discretization scheme.
	 *
	 * @param initialValue Spot value
	 * @param riskFreeRate The risk free rate
	 * @param volatility The log volatility
	 * @param brownianMotion The brownian motion driving the model.
	 */
	public MonteCarloBlackScholesModel(
			final double initialValue,
			final double riskFreeRate,
			final double volatility,
			final BrownianMotion brownianMotion) {
		super();

		// Create the model
		model = new BlackScholesModel(initialValue, riskFreeRate, volatility);
		process = new EulerSchemeFromProcessModel(model, brownianMotion);
	}

	/**
	 * Create a Monte-Carlo simulation using given time discretization.
	 *
	 * @param timeDiscretization The time discretization.
	 * @param numberOfPaths The number of Monte-Carlo path to be used.
	 * @param initialValue Spot value.
	 * @param riskFreeRate The risk free rate.
	 * @param volatility The log volatility.
	 */
	public MonteCarloBlackScholesModel(
			final TimeDiscretization timeDiscretization,
			final int numberOfPaths,
			final double initialValue,
			final double riskFreeRate,
			final double volatility) {
		this(
				initialValue, riskFreeRate, volatility,
				new BrownianMotionFromMersenneRandomNumbers(
						timeDiscretization, 1 /* numberOfFactors */, numberOfPaths, seed));
	}

	/**
	 * Create a Monte-Carlo simulation using given time discretization.
	 *
	 * @param model The model.
	 * @param process The process.
	 */
	private MonteCarloBlackScholesModel(
			final BlackScholesModel model,
			final MonteCarloProcess process) {
		super();

		this.model = model;
		this.process = process;
	}

	@Override
	public RandomVariable getAssetValue(final double time, final int assetIndex) throws CalculationException {
		return getAssetValue(getTimeIndex(time), assetIndex);
	}

	@Override
	public RandomVariable getAssetValue(final int timeIndex, final int assetIndex) throws CalculationException {
		return process.getProcessValue(timeIndex, assetIndex);
	}

	@Override
	public RandomVariable getNumeraire(final int timeIndex) throws CalculationException {
		final double time = getTime(timeIndex);

		return model.getNumeraire(process, time);
	}

	@Override
	public RandomVariable getNumeraire(final double time) throws CalculationException {
		return model.getNumeraire(process, time);
	}

	@Override
	public RandomVariable getMonteCarloWeights(final double time) throws CalculationException {
		return getMonteCarloWeights(getTimeIndex(time));
	}

	@Override
	public int getNumberOfAssets() {
		return 1;
	}

	@Override
	public AssetModelMonteCarloSimulationModel getCloneWithModifiedData(final Map<String, Object> dataModified) {
		/*
		 * Create a new model with the new model parameters.
		 */
		final BlackScholesModel newModel = model.getCloneWithModifiedData(dataModified);

		/*
		 * Create a new BrownianMotion, if requested.
		 */
		final int		newSeed			= dataModified.get("seed") != null			? ((Number)dataModified.get("seed")).intValue()				: seed;

		BrownianMotion newBrownianMotion;
		if(dataModified.get("seed") != null) {
			// The seed has changed. Hence we have to create a new BrownianMotionLazyInit.
			newBrownianMotion = new BrownianMotionFromMersenneRandomNumbers(this.getTimeDiscretization(), 1, this.getNumberOfPaths(), newSeed);
		}
		else {
			// The seed has not changed. We may reuse the random numbers (Brownian motion) of the original model
			newBrownianMotion = (BrownianMotion)process.getStochasticDriver();
		}

		final double newInitialTime	= dataModified.get("initialTime") != null	? ((Number)dataModified.get("initialTime")).doubleValue() : getTime(0);

		final double timeShift = newInitialTime - getTime(0);
		if(timeShift != 0) {
			final TimeDiscretization newTimeDiscretization =  process.getStochasticDriver().getTimeDiscretization().getTimeShiftedTimeDiscretization(timeShift);
			newBrownianMotion = newBrownianMotion.getCloneWithModifiedTimeDiscretization(newTimeDiscretization);
		}

		// Create a corresponding MC process
		final MonteCarloProcessFromProcessModel process = new EulerSchemeFromProcessModel(getModel(), new BrownianMotionFromMersenneRandomNumbers(this.getTimeDiscretization(), 1 /* numberOfFactors */, this.getNumberOfPaths(), seed));

		return new MonteCarloBlackScholesModel(newModel, process);
	}

	@Override
	public AssetModelMonteCarloSimulationModel getCloneWithModifiedSeed(final int seed) {
		// Create a corresponding MC process
		final MonteCarloProcessFromProcessModel process = new EulerSchemeFromProcessModel(getModel(), new BrownianMotionFromMersenneRandomNumbers(this.getTimeDiscretization(), 1 /* numberOfFactors */, this.getNumberOfPaths(), seed));
		return new MonteCarloBlackScholesModel(model, process);
	}

	@Override
	public int getNumberOfPaths() {
		return process.getNumberOfPaths();
	}

	@Override
	public LocalDateTime getReferenceDate() {
		return model.getReferenceDate();
	}

	@Override
	public TimeDiscretization getTimeDiscretization() {
		return process.getTimeDiscretization();
	}

	@Override
	public double getTime(final int timeIndex) {
		return process.getTime(timeIndex);
	}

	@Override
	public int getTimeIndex(final double time) {
		return process.getTimeIndex(time);
	}

	/* (non-Javadoc)
	 * @see net.finmath.montecarlo.MonteCarloSimulationModel#getRandomVariableForConstant(double)
	 * @TODO Move this to base class
	 */
	@Override
	public RandomVariable getRandomVariableForConstant(final double value) {
		return model.getRandomVariableForConstant(value);
	}

	@Override
	public RandomVariable getMonteCarloWeights(final int timeIndex) throws CalculationException {
		return process.getMonteCarloWeights(timeIndex);
	}

	/**
	 * Returns the {@link BlackScholesModel} used for this Monte-Carlo simulation.
	 *
	 * @return the model
	 */
	public BlackScholesModel getModel() {
		return model;
	}

	/**
	 * Returns the {@link MonteCarloProcess} used for this Monte-Carlo simulation.
	 *
	 * @return the process
	 */
	public MonteCarloProcess getProcess() {
		return process;
	}
}
