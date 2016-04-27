package com.genevieveluyt.multiplayercardgames;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * Created by Genevieve on 03/09/2015.
 *
 * Crazy Eights Rules
 *
 * Goal: Get rid of all your cards
 * Basic Gameplay: Play a single card with the same rank or suit as the pile or an 8. If the player
 * has no such card, draw cards until they do. A player can choose to draw cards even if they
 * have valid cards in their hand
 *
 * Special cards:
 * 8	-	declare a suit which the next player must play (or another 8)
 *
 */
public class CrazyEightsGameBoard extends GameBoard {

	private static final int STARTING_HAND = 7;

	static Activity activity;

    // Layouts
	LinearLayout gameLayout;
	LinearLayout handLayout;
	HorizontalScrollView oppLayout;   // TODO move to parent class?
	TextView mustPlayView;
	static Button finishButton;

	// Game variables
	//HashMap<String, Hand> hands; in parent class
	String currPlayerId;
	ArrayList<String> playerIds;
	Hand currHand;
	Deck drawDeck;
	Deck playDeck;
	static Dialog chooseSuitDialog;

	// Gameplay variables
	boolean hasPlayed; 			// current player has placed a card on the play deck
	static int chosenSuit;		// suit chosen after playing an 8
	int mustPlaySuit;			// suit chosen by previous player after playing an 8

	View.OnClickListener deckClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()){
				case R.id.drawdeck_view:
					currHand.draw(drawDeck);
					if (drawDeck.isEmpty()) {
						// if ran out of cards to draw, reshuffle the play deck and use as draw deck
						Card topCard = playDeck.drawVirtual();
						drawDeck = playDeck;
						drawDeck.reshuffle();
						activity.findViewById(R.id.drawdeck_view).setVisibility(View.VISIBLE);
						playDeck = new Deck(Deck.EMPTY, true, (ImageView) activity.findViewById(R.id.playdeck_view));
						playDeck.add(topCard);
					}
					break;
				case R.id.playdeck_view:
					if (validPlay()) {
						currHand.playSelected(playDeck);
						hasPlayed = true;
						if (currHand.isEmpty()) {
							BaseGameUtils.makeSimpleDialog(activity, activity.getString(R.string.you_won)).show();
							finishButton.performClick();
						} else if (playDeck.peek().getRank() == 8) {
							finishButton.setClickable(false);
							chooseSuitDialog.show();
						}
					} else if (hasPlayed && playDeck.peek().getRank() == 8)
						chooseSuitDialog.show();
			}
		}
	};

	View.OnClickListener handClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (!hasPlayed) currHand.select(new Card(v.getId()));
		}
	};

	public CrazyEightsGameBoard(String currPlayerId, ArrayList<String> playerIds, byte[] data, Activity activity) {
		super();
		this.currPlayerId = currPlayerId;
		this.playerIds = playerIds;
		this.gameLayout = (LinearLayout) activity.findViewById(R.id.gameplay_layout);
		this.handLayout = (LinearLayout) activity.findViewById(R.id.hand_layout);
		this.oppLayout = (HorizontalScrollView) activity.findViewById(R.id.opponent_scroll_layout); // TODO temp
		mustPlayView = (TextView) activity.findViewById(R.id.must_play_suit);
		finishButton = (Button) activity.findViewById(R.id.finish_button);
		this.activity = activity;
		hasPlayed = false;
		chosenSuit = 0;
		mustPlaySuit = 0;
		if (data == null)
			initBoard();
		else {
			loadData(data);
			activateGUI();
		}
	}

	@Override
	public void initBoard() {
		if (MainActivity.DEBUG) System.out.println("CrazyEightsGameBoard|initBoard(): Initializing game board");
		drawDeck = new Deck(Deck.STANDARD);
		playDeck = new Deck(Deck.EMPTY);
		for (String player : playerIds) {
			hands.put(player, new Hand(drawDeck, STARTING_HAND));
			if (MainActivity.DEBUG) System.out.println(player + " hand: " + hands.get(player));
		}
		playDeck.addVirtual(drawDeck.drawVirtual());
	}

	/* Data format:
		game id | draw deck data | play deck data | chosen suit | playerIds and their hand data
	 */
	@Override
	public byte[] saveData() {
		if (MainActivity.DEBUG) System.out.println("CrazyEightsGameBoard|loadData(byte[]): Saving game data");
        StringBuilder dataStr = new StringBuilder();
		dataStr.append(getGameType()).append(separator)
        .append(drawDeck.getData()).append(separator)
        .append(playDeck.getData()).append(separator)
		.append(chosenSuit).append(separator);
        for (String playerId : playerIds) {
	        dataStr.append(playerId).append(separator)
            .append(hands.get(playerId).getData())
            .append(separator);
        }

        return dataStr.toString().getBytes(Charset.forName("UTF-8"));
	}

	@Override
	public void loadData(byte[] data) {
		if (MainActivity.DEBUG) System.out.println("CrazyEightsGameBoard|loadData(byte[]): Loading game data");
		handLayout.removeAllViews();
        String dataStr = new String(data, Charset.forName("UTF-8"));
        String[] dataArr = dataStr.split(String.valueOf(separator));
        drawDeck = new Deck(dataArr[1], false, (ImageView) gameLayout.findViewById(R.id.drawdeck_view));
        playDeck = new Deck(dataArr[2], true, (ImageView) gameLayout.findViewById(R.id.playdeck_view));
		mustPlaySuit = Integer.parseInt(dataArr[3]);
        for (int i = 4; i < dataArr.length; i+=2) {
	        String playerId = dataArr[i];
	        if (playerId.equals(currPlayerId)) {
		        currHand = new Hand(dataArr[i+1], handLayout, handClickListener);
		        hands.put(playerId, currHand);
	        } else
		        hands.put(playerId, new Hand(dataArr[i+1]));  // hand data starts after the two decks
	        if (MainActivity.DEBUG) System.out.println(playerId + " hand: " + hands.get(playerId));
        }
	}

	@Override
	public int getGameType() {
		return GameBoard.CRAZY_EIGHTS;
	}

	private void activateGUI() {
		gameLayout.findViewById(R.id.drawdeck_view).setOnClickListener(deckClickListener);
		gameLayout.findViewById(R.id.playdeck_view).setOnClickListener(deckClickListener);

		if (mustPlaySuit == 0)
			mustPlayView.setText("");
		else {
			String str = activity.getString(R.string.you_must_play);
			str += " " + (Card.suitToString(mustPlaySuit));
			mustPlayView.setText(str);
		}

		android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
		builder.setTitle(R.string.choose_suit)
				.setItems(R.array.suits, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						chosenSuit = which+1;
						if (MainActivity.DEBUG)
							System.out.println("CrazyEightsGameBoard|activateGUI(): chose suit " + Card.suitToString(chosenSuit));
						finishButton.setClickable(true);
					}
				});
		chooseSuitDialog = builder.create();
	}

	// Returns true if there is a valid play without drawing
	private boolean canPlay() {
		for (Card card : currHand) {
			if (validPlay(card))
				return true;
		}
		return false;
	}

	// Returns true if selected cards are a valid play
	private boolean validPlay() {
		for (Card card : currHand.getSelected())
			if (!validPlay(card))
				return false;
		return true;
	}

	// Returns true if @param card is a valid play
	private boolean validPlay(Card card) {
		Card target = playDeck.peek();
		// if previous player played an 8
		if (mustPlaySuit != 0) {
			return (card.getSuit() == mustPlaySuit || card.getRank() == 8);
		}
		return (card.getSuit() == target.getSuit() || card.getRank() == target.getRank() || card.getRank() == 8);
	}
}
