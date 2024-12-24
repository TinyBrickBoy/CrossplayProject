local ReplicatedStorage = game:GetService('ReplicatedStorage')

local remote_img = require(game.ReplicatedStorage.remote_img)
local asset_service = game:GetService("AssetService");


ReplicatedStorage:WaitForChild("loadPlayerSkin").OnClientEvent:Connect(function(uuid, player, Character)

	local skin = remote_img.create_image("https://crossplayproject.xyz/api/uuid/"..uuid.."/skin")
	task.wait()
	for _, part in pairs(Character.SecondLayer:GetChildren()) do
		if part:IsA("MeshPart") then
			part.TextureContent = Content.fromObject(skin)
		end
	end

	for _, part in pairs(Character:GetChildren()) do
		if part:IsA("MeshPart") then
			part.TextureContent = Content.fromObject(skin)
		end
	end

end)