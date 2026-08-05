package com.loja.ordercheckout.domain.model;

/**
 * One bar of the "Units Sold by Category" chart (backlog S21). A product that
 * belongs to several categories contributes its units to each of them.
 */
public record CategoryUnits(String categoryName, long unitsSold) { }
