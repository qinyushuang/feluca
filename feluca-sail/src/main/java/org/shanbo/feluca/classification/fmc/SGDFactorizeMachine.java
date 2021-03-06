package org.shanbo.feluca.classification.fmc;

import java.util.Arrays;
import java.util.Properties;
import java.util.Random;

import org.shanbo.feluca.classification.common.Evaluator;
import org.shanbo.feluca.classification.lr.AbstractSGDLogisticRegression;
import org.shanbo.feluca.classification.lr.SGDL2LR;
import org.shanbo.feluca.data2.DataEntry;
import org.shanbo.feluca.data2.Vector;

/**
 * only support 2-degree interaction of features.
 * Extending AbstractSGDLogisticRegression 
 * 
 * @author lgn
 *
 */
public class SGDFactorizeMachine extends SGDL2LR{

	protected int dim = 5;
	protected float fWeightRange = 0.3f;
	protected float[][] factors ;

	
	Random rand = new Random();

	protected void init(){
		super.init(); 
		factors = new float[dim][];
		for(int k = 0 ; k < dim ; k ++){
			factors[k] = new float[maxFeatureId + 1];
			for(int i = 0 ; i < factors[k].length ; i++){
				factors[k][i] = rand.nextFloat() * fWeightRange;
			}
		}
	}

	/**
	 * just for one-hot dataset
	 */
	protected void _train(int fold, int remain) throws Exception{
//		if (true){
//			super._train(fold, remain);
//			return;
//		}
		System.out.println("one hot");
		double avge = 99999.9;
		double lastAVGE = Double.MAX_VALUE;

		double corrects  = 0;
		double lastCorrects = -1;


		double multi = (biasWeightRound * minSamples + maxSamples)/(minSamples + maxSamples + 0.0);

		for(int l = 0 ; l < Math.max(10, loops)
						&& (l < Math.min(10, loops) 
						|| (l < loops && (Math.abs(1- avge/ lastAVGE) > convergence )
						|| Math.abs(1- corrects/ lastCorrects) > convergence * 0.01)); l++){
			lastAVGE = avge;
			lastCorrects = corrects;
			dataEntry.reOpen(); //start reading data

			long timeStart = System.currentTimeMillis();

			int c =1; //for n-fold cv
			double error = 0;
			double sume = 0;
			corrects = 0;
			int cc = 0;

			for(Vector sample = dataEntry.getNextVector(); sample != null ; sample = dataEntry.getNextVector()){
				if (c % fold == remain){ // no train
					;
				}else{ //train
					if ( sample.getIntHeader() == this.biasLabel){ //bias; sequentially compute #(bias - 1) times
						for(int bw = 1 ; bw < this.biasWeightRound; bw++){ //bias
							this.gradientDescendOneHot(sample);
						}
					}
					error = gradientDescendOneHot(sample);
					if (Math.abs(error) < 0.45)//accuracy
						if ( sample.getIntHeader() == this.biasLabel)
							corrects += this.biasWeightRound;
						else
							corrects += 1; 
					cc += 1;
					sume += Math.abs(error);
				}
				c += 1;
			}

			avge = sume / cc;

			long timeEnd = System.currentTimeMillis();
			double acc = corrects / (cc * multi) * 100;

			if (corrects  < lastCorrects ){ //
				if (!alphaSetted){
					this.alpha *= 0.5;
					if (alpha < minAlpha)
						alpha = minAlpha;
				}
				if (!lambdaSetted){
					this.lambda *= 0.9;
					if (lambda < minLambda)
						lambda = minLambda;
				}
			}
			System.out.println(String.format("#%d loop%d\ttime:%d(ms)\tacc: %.3f(approx)\tavg_error:%.6f", cc, l, (timeEnd - timeStart), acc , avge));
		}
	}

	public final double gradientDescendOneHot(Vector sample){
		double oneDegreeWeightSum = 0;
		for(int i = 0 ; i < sample.getSize(); i++){
			oneDegreeWeightSum += featureWeights[sample.getFId(i)];
		}
		
		double intersectionWeightSum = 0;
		double[] Sigmav2x2 = new double[dim];
		double[] SigmaVX = new double[dim];
		for(int f = 0; f < dim ; f ++){
			for(int i = 0 ; i < sample.getSize(); i++){
				SigmaVX[f] += factors[f][sample.getFId(i)] ;
				Sigmav2x2[f] += Math.pow(factors[f][sample.getFId(i)], 2) ;
			}
			intersectionWeightSum += ((Math.pow(SigmaVX[f], 2)  - Sigmav2x2[f]));
		}
		double tmp = Math.pow(Math.E, -(oneDegreeWeightSum + (intersectionWeightSum * 0.5))); //e^-sigma(x)
		double error = outerLabelInfo[LABELRANGEBASE + sample.getIntHeader()][0] - (1/ (1+tmp)); 
		
		//-----------w & v[]--------,
		double partialDerivation =   tmp  / (tmp * tmp + 2 * tmp + 1) ;

		for(int i = 0 ; i < sample.getSize(); i++){
			// w <- w + alpha * (error * 1 * partial_derivation - lambda * w) 
			featureWeights[sample.getFId(i)] += 
					alpha * (error *  partialDerivation - lambda * featureWeights[sample.getFId(i)]) ;
			//v[] : 1/2 * 2(sigmaVX - x)
			for(int f = 0 ; f < dim ; f++){
				double step = (SigmaVX[f] - factors[f][sample.getFId(i)]) ;
				factors[f][sample.getFId(i)] += alpha * (error * step * partialDerivation - lambda * factors[f][sample.getFId(i)] ) ;
			}
		}
		//----------v[]-------
		return error;
	}
	
	public final double gradientDescend(Vector sample){
		double oneDegreeWeightSum = 0;
		for(int i = 0 ; i < sample.getSize(); i++){
			oneDegreeWeightSum += featureWeights[sample.getFId(i)] * sample.getWeight(i);
		}
		
		double intersectionWeightSum = 0;
		double[] Sigmav2x2 = new double[dim];
		double[] SigmaVX = new double[dim];
		for(int f = 0; f < dim ; f ++){
			for(int i = 0 ; i < sample.getSize(); i++){
				SigmaVX[f] += factors[f][sample.getFId(i)] * sample.getWeight(i);
				Sigmav2x2[f] += Math.pow(factors[f][sample.getFId(i)], 2) * Math.pow(sample.getWeight(i), 2);
			}
			intersectionWeightSum += ((Math.pow(SigmaVX[f], 2)  - Sigmav2x2[f]));
		}
		double tmp = Math.pow(Math.E, -(oneDegreeWeightSum + (intersectionWeightSum * 0.5))); //e^-sigma(x)
		double error = outerLabelInfo[LABELRANGEBASE + sample.getIntHeader()][0] - (1/ (1+tmp)); 
		
		//-----------w & v[]--------,
		double partialDerivation =   tmp  / (tmp * tmp + 2 * tmp + 1) ;

		for(int i = 0 ; i < sample.getSize(); i++){
			// w <- w + alpha * (error * partial_derivation - lambda * w) 
			featureWeights[sample.getFId(i)] += 
					alpha * (error * sample.getWeight(i) * partialDerivation - lambda * featureWeights[sample.getFId(i)]) ;
			//v[] 
			for(int f = 0 ; f < dim ; f++){
				double step = (SigmaVX[f] * sample.getWeight(i) - factors[f][sample.getFId(i)] * sample.getWeight(i) * sample.getWeight(i)) * 2;
				factors[f][sample.getFId(i)] += alpha * (error * step * partialDerivation - lambda * factors[f][sample.getFId(i)] ) ;
			}
		}
		//----------v[]-------
		return error;
	}
	
	@Override
	public void setProperties(Properties prop) {
		super.setProperties(prop);
		this.dim = new Integer(prop.getProperty("dim", "5"));
	}

	@Override
	public Properties getProperties() {
		// TODO Auto-generated method stub
		return null;
	}



	@Override
	public void predict(Vector sample, double[] probabilities) throws Exception {
		double oneDegreeWeightSum = 0;
		for(int i = 0 ; i < sample.getSize(); i++){
			oneDegreeWeightSum += featureWeights[sample.getFId(i)] * sample.getWeight(i);
		}
		
		double intersectionWeightSum = 0;
		double[] Sigmav2x2 = new double[dim];
		double[] SigmaVX = new double[dim];
		for(int f = 0; f < dim ; f ++){
			for(int i = 0 ; i < sample.getSize(); i++){
				SigmaVX[f] += factors[f][sample.getFId(i)] * sample.getWeight(i);
				Sigmav2x2[f] += Math.pow(factors[f][sample.getFId(i)], 2) * Math.pow(sample.getWeight(i), 2);
			}
			intersectionWeightSum += ((Math.pow(SigmaVX[f], 2)  - Sigmav2x2[f]));
		}

		double probability = 1/(1+Math.pow(Math.E, -(oneDegreeWeightSum + (intersectionWeightSum * 0.5))));
		probabilities[0] =  1- probability;
		probabilities[1] =  probability;
		
	}

	@Override
	public void saveModel(String filePath) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void loadModel(String modelPath, Properties statistic)
			throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void crossValidation(int fold, Evaluator... evaluators)
			throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void estimateParameter() throws NullPointerException {
		// TODO Auto-generated method stub
		
	}


	@Override
	public int estimate(Properties dataStatus, Properties parameters) {
		// TODO Auto-generated method stub
		return 0;
	}

}
