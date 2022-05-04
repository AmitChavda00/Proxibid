package com.proxibid.controller;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.proxibid.entity.Auction;
import com.proxibid.entity.Bidder;
import com.proxibid.entity.BidderCart;
import com.proxibid.entity.CartItem;
import com.proxibid.service.AuctionService;
import com.proxibid.service.BidderService;
import com.proxibid.service.CartService;
import com.proxibid.service.CategoryService;
import com.proxibid.service.LiveBidService;
import com.proxibid.service.AuctioneerService;
import com.proxibid.util.CookieUtil;
import com.proxibid.util.JwtUtil;
import com.proxibid.util.PaymentStatus;
import com.proxibid.util.ROLE;

@Controller
public class BidderController {

	@Autowired
	private BidderService bidderService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private AuctionService auctionService;

	@Autowired
	private AuctioneerService auctioneerService;

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private LiveBidService liveBidService;

	@Autowired
	private CartService cartService;

	/* Web App Controller */

	@RequestMapping(value = "/bidder/cart")
	public String bidWinnerCart(Model model, HttpServletRequest request) {

		String username = CookieUtil.getCookieByName(request, "username");
		BidderCart cart = cartService.findByBidderId(username);

		if (cart == null) {
			model.addAttribute("cart", cart);
			return "bidder/cart";
		}

		// get only those items which are not paid
		List<CartItem> items = new ArrayList<>();
		List<CartItem> cartItems = cart.getCartItems();

		double total = 0l;
		for (CartItem item : cartItems) {
			if (!item.getPaymentStatus().equals(PaymentStatus.PAID.toString())) {
				items.add(item);
				total += item.getPrice();
			}
		}
		cart.setCartItems(items);
		cart.setTotalAmount(total);

		model.addAttribute("cart", cart);
		return "/bidder/cart";
	}

	@RequestMapping(value = "/bidder/signup")
	public String bidderSignUp(@ModelAttribute Bidder bidder) {
		return "/bidder/signup";
	}

	@RequestMapping(value = "/bidder/dashboard", method = RequestMethod.GET)
	public String bidderdashboard(Model model) {

		model.addAttribute("upcomingTodaysAuctions", auctionService.findTodaysUpcomingEvents());
		model.addAttribute("auctions", auctionService.findAllLiveEvents());
		model.addAttribute("categories", categoryService.getAllCategories());
		return "/bidder/dashboard";
	}

	@RequestMapping(value = "/bidder/dashboard/")
	public String postitembycategories(@RequestParam("checkbox") ArrayList<String> selectedCategory, Model model) {
		model.addAttribute("upcomingTodaysAuctions", auctionService.findTodaysUpcomingEvents());
		model.addAttribute("auctions", auctionService.findAllLiveEvents());
		List<Auction> filteredAuctions = new ArrayList<>();
		for (int i = 0; i < selectedCategory.size(); i++) {
			filteredAuctions.addAll(auctionService.getAllLiveEventsByCategory(selectedCategory.get(i)));
		}
		model.addAttribute("auctions", filteredAuctions);
		model.addAttribute("categories", categoryService.getAllCategories());
		return "/bidder/dashboard";
	}

	@RequestMapping(value = "/bidder/event/{eventno}", method = RequestMethod.GET)
	public String bidderEventPageGet(@PathVariable("eventno") long eventNo, Model model) {

		Auction current_auction = auctionService.findByeventNo(eventNo);
		model.addAttribute("items", current_auction);

		model.addAttribute("eventNumber", eventNo);

		model.addAttribute("auctionHouseName",
				auctioneerService.findByEmail(current_auction.getSellerId()).getHouseName());

		Auction auction = auctionService.findByeventNo(eventNo);
		model.addAttribute("catalog", auction.getItems());
		return "/bidder/view-auction";
	}

	@RequestMapping(value = "/bidder/event/", method = RequestMethod.POST)
	public String bidderEventPagePost() {
		return "event";
	}

	@RequestMapping(value = "/bidder/live-auction/{eventNo}", method = RequestMethod.GET)
	public String liveAuctionPost(@PathVariable("eventNo") long eventNo, Model model,
			HttpServletRequest httpServletRequest) {

		model.addAttribute("liveItems", liveBidService.findAllByAuctionId(eventNo));

		model.addAttribute("items", auctionService.findByeventNo(eventNo));

		Auction a = (Auction) auctionService.findByeventNo(eventNo);
		model.addAttribute("catalog", a.getItems());
		model.addAttribute("eventNo", eventNo);

		String authorizationHeader = CookieUtil.getCookieByName(httpServletRequest, "token");
		String username = CookieUtil.getCookieByName(httpServletRequest, "username");

		model.addAttribute("bidderEmail", jwtUtil.extractUsername(authorizationHeader));
		model.addAttribute("bidderId", username);
		return "/bidder/live-auction";
	}

	@RequestMapping(value = "/bidder/live-auction", method = RequestMethod.POST)
	public String liveAuctionGet(Model model) {
		return "bidder/live-auction";
	}

	@RequestMapping(value = "/bidder/history")
	public String histroy(HttpServletRequest request, Model model) {

		String username = CookieUtil.getCookieByName(request, "username");
		BidderCart cart = cartService.findByBidderId(username);

		// get only those items which are not paid
		List<CartItem> items = new ArrayList<>();
		cart.getCartItems().forEach(item -> {
			if (item.getPaymentStatus().equals(PaymentStatus.PAID.toString())) {
				items.add(item);
			}
		});

		if (items.size() == 0) {
			model.addAttribute("items", null);
		} else {
			model.addAttribute("items", items);
		}

		return "/bidder/history";
	}

	/* Rest Controller */

	@RequestMapping(value = "/public/bidder/checkout")
	public String bidderSignUp(HttpServletRequest request) {
		String username = "";
		Cookie[] cookies = request.getCookies();
		for (Cookie c : cookies) {
			if (c.getName().equals("username")) {
				username = c.getValue();
			}
		}
		BidderCart cart = cartService.findByBidderId(username);
		// set all cart items to paid
		List<CartItem> cartItems = cart.getCartItems();
		for (CartItem cartItem : cartItems) {
			cartItems.get(cartItems.indexOf(cartItem)).setPaymentStatus(PaymentStatus.PAID.toString());
		}
		cart.setCartItems(cartItems);
		cart.setTotalAmount(0l);
		cartService.save(cart);
		return "success";
	}

	@RequestMapping(value = "/bidder/signup/save")
	public String bidderSignInAfterSignUp(@ModelAttribute Bidder bidder, HttpServletRequest request) {
		request.setAttribute("error", null);
		if (bidderService.bidderExistsByEmail(bidder.getBidderEmail())) {
			request.setAttribute("error", "User with same email already exixst!");
			return "bidder-signup";
		} else {
			bidder.setBidderPassword(new BCryptPasswordEncoder().encode(bidder.getBidderPassword()));
			bidder.setRole(ROLE.ROLE_BIDDER.toString());
			bidderService.bidderSignUp(bidder);
			return "redirect:/login";
		}
	}

}
