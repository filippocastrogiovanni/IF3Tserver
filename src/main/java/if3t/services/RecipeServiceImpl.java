package if3t.services;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import if3t.exceptions.TriggerChannelNotAuthorizedException;
import if3t.exceptions.ActionChannelNotAuthorizedException;
import if3t.exceptions.NotLoggedInException;
import if3t.models.Channel;
import if3t.models.Recipe;
import if3t.models.User;
import if3t.repositories.RecipeRepository;
import if3t.repositories.UserRepository;

@Service
@Transactional
public class RecipeServiceImpl implements RecipeService {

	@Autowired
	private RecipeRepository recipeRepository;
	@Autowired
	private UserRepository userRepository;
	
	public List<Recipe> readUserRecipes(Long userId) {
		return recipeRepository.findByUser_Id(userId);
	}

	public List<Recipe> readPublicRecipes() {
		return recipeRepository.findByIsPublic(true);
	}

	public List<Recipe> readRecipe(Long id) {
		Recipe recipe = recipeRepository.findOne(id);
		String groupId = recipe.getGroupId();
		return recipeRepository.findByGroupId(groupId);
	}

	public void deleteRecipe(Long id) {
		Recipe recipe = recipeRepository.findOne(id);
		recipeRepository.delete(recipe);
	}

	public void addRecipe(List<Recipe> recipe) {
		UUID groupId = UUID.randomUUID();
		for(Recipe r: recipe){
			r.setGroupId(groupId.toString());
			recipeRepository.save(r);
		}
	}

	public void publishRecipe(Recipe recipe) {
		recipeRepository.save(recipe);
	}

	public void enableRecipe(Recipe recipe) throws NotLoggedInException, TriggerChannelNotAuthorizedException, ActionChannelNotAuthorizedException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		User user = null;
		if (auth != null)
			user = userRepository.findByUsername(auth.getName());
		
		if(user == null)
			throw new NotLoggedInException();
		
		Long userId = user.getId();
		Long triggerChannelId = recipe.getTrigger().getChannel().getChannelId();
		Long actionChannelId = recipe.getAction().getChannel().getChannelId();
		
		if(userRepository.findByIdAndChannels_ChannelId(userId, triggerChannelId) == null)
			throw new TriggerChannelNotAuthorizedException();
		
		if(userRepository.findByIdAndChannels_ChannelId(userId, actionChannelId) == null)
			throw new ActionChannelNotAuthorizedException();
		
		recipe.setIsEnabled(true);
		recipeRepository.save(recipe);
		
	}

}
