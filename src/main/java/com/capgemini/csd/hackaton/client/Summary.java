package com.capgemini.csd.hackaton.client;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Summary {

	private int sensorType;
	private Long min = null;
	private Long max = null;
	private BigDecimal total = BigDecimal.valueOf(0);
	private long count = 0;

	public Summary(int sensorType) {
		super();
		this.sensorType = sensorType;
	}

	public int getSensorType() {
		return sensorType;
	}

	public Long getMin() {
		return min;
	}

	public Long getMax() {
		return max;
	}

	public BigDecimal getAverage() {
		return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_DOWN);
	}

	public void accept(long i) {
		min = min(min, i);
		max = max(max, i);
		total = total.add(BigDecimal.valueOf(i));
		count++;
	}

	public void combine(Summary other) {
		min = min(min, other.min);
		max = max(max, other.max);
		total = total.add(other.total);
		count += other.count;
	}

	private Long max(Long max1, Long max2) {
		if (max1 == null) {
			return max2;
		} else if (max2 == null) {
			return max1;
		} else {
			return Math.max(max1, max2);
		}
	}

	private Long min(Long min1, Long min2) {
		if (min1 == null) {
			return min2;
		} else if (min2 == null) {
			return min1;
		} else {
			return Math.min(min1, min2);
		}
	}

}
